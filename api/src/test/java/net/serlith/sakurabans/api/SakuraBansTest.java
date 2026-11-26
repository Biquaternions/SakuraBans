package net.serlith.sakurabans.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SakuraBansTest {

    private static final SakuraBansApi API = api();
    private static final SakuraBansApi OTHER = api();

    private static SakuraBansApi api() {
        return (SakuraBansApi) Proxy.newProxyInstance(SakuraBansApi.class.getClassLoader(),
                new Class<?>[] {SakuraBansApi.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    @AfterEach
    void unregister() {
        SakuraBans.unregister(API);
        SakuraBans.unregister(OTHER);
    }

    @Test
    void theApiIsMissingUntilRegistered() {
        assertThat(SakuraBans.find()).isEmpty();
        assertThatThrownBy(SakuraBans::api).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void registerAndUnregister() {
        SakuraBans.register(API);

        assertThat(SakuraBans.api()).isSameAs(API);
        assertThat(SakuraBans.unregister(OTHER)).isFalse();
        assertThat(SakuraBans.unregister(API)).isTrue();
        assertThat(SakuraBans.find()).isEmpty();
    }

    @Test
    void onlyOneApiIsRegisteredAtOnce() {
        SakuraBans.register(API);

        assertThatThrownBy(() -> SakuraBans.register(OTHER))
                .isInstanceOf(IllegalStateException.class);
        assertThat(SakuraBans.api()).isSameAs(API);
    }
}
