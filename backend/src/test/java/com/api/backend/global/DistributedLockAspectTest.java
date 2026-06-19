package com.api.backend.global;

import com.api.backend.global.exception.LockAcquisitionFailedException;
import com.api.backend.global.lock.DistributedLock;
import com.api.backend.global.lock.DistributedLockAspect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("분산락 Aspect 테스트")
class DistributedLockAspectTest {

    @Mock RedissonClient redissonClient;
    @Mock RLock rLock;

    // AspectJ 프록시로 Aspect를 직접 적용하는 테스트용 타겟 서비스
    static class LockTarget {
        int callCount = 0;

        @DistributedLock(key = "'test:lock:' + #id", leaseTime = 3)
        public String doWork(Long id) {
            callCount++;
            return "done:" + id;
        }

        @DistributedLock(key = "'test:lock:' + #id", leaseTime = 3)
        public void doWorkAndThrow(Long id) {
            throw new RuntimeException("작업 중 예외");
        }
    }

    private LockTarget proxied() {
        LockTarget target = new LockTarget();
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(new DistributedLockAspect(redissonClient));
        return factory.getProxy();
    }

    @Test
    @DisplayName("락 획득 성공 시 메서드가 실행되고 락이 해제된다")
    void 락_획득_성공_메서드_실행_후_해제() throws Exception {
        given(redissonClient.getLock("test:lock:1")).willReturn(rLock);
        given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(true);

        LockTarget target = proxied();
        String result = target.doWork(1L);

        assertThat(result).isEqualTo("done:1");
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("락 획득 실패 시 LockAcquisitionFailedException이 발생한다")
    void 락_획득_실패_예외() throws Exception {
        given(redissonClient.getLock("test:lock:2")).willReturn(rLock);
        given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(false);

        LockTarget target = proxied();

        assertThatThrownBy(() -> target.doWork(2L))
            .isInstanceOf(LockAcquisitionFailedException.class);
        verify(rLock, never()).unlock();
    }

    @Test
    @DisplayName("메서드 예외 발생 시 락이 반드시 해제된다")
    void 메서드_예외_시_락_해제_보장() throws Exception {
        given(redissonClient.getLock("test:lock:3")).willReturn(rLock);
        given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(true);

        LockTarget target = proxied();

        assertThatThrownBy(() -> target.doWorkAndThrow(3L))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("작업 중 예외");

        verify(rLock).unlock();
    }

    @Test
    @DisplayName("스레드가 락을 보유하지 않으면 unlock을 호출하지 않는다")
    void 락_미보유_시_unlock_미호출() throws Exception {
        given(redissonClient.getLock("test:lock:4")).willReturn(rLock);
        given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(false);

        LockTarget target = proxied();
        target.doWork(4L);

        verify(rLock, never()).unlock();
    }
}
