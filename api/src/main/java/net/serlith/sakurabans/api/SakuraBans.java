package net.serlith.sakurabans.api;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

/**
 * Static access to the {@link SakuraBansApi}. It is available while SakuraBans is enabled:
 * from SakuraBans' enable until its disable, so depend on SakuraBans (or load after it) and look
 * it up when you need it rather than once at class-load time.
 */
public final class SakuraBans {

    private static final AtomicReference<@Nullable SakuraBansApi> API = new AtomicReference<>();

    private SakuraBans() {
    }

    /**
     * The API.
     *
     * @return the API
     * @throws IllegalStateException if SakuraBans isn't enabled
     */
    public static SakuraBansApi api() {
        final SakuraBansApi api = API.get();
        if (api == null) {
            throw new IllegalStateException("SakuraBans is not enabled");
        }
        return api;
    }

    /**
     * The API, if SakuraBans is enabled.
     *
     * @return the API, or empty
     */
    public static Optional<SakuraBansApi> find() {
        return Optional.ofNullable(API.get());
    }

    /**
     * Makes the API available. Called by SakuraBans when it enables; other plugins must not call
     * it.
     *
     * @param api the API
     * @throws IllegalStateException if an API is already registered
     */
    public static void register(final SakuraBansApi api) {
        Objects.requireNonNull(api, "api");
        if (!API.compareAndSet(null, api)) {
            throw new IllegalStateException("the SakuraBans API is already registered");
        }
    }

    /**
     * Withdraws the API. Called by SakuraBans when it disables; other plugins must not call it.
     *
     * @param api the API that was registered
     * @return {@code true} if it was registered and is now withdrawn
     */
    public static boolean unregister(final SakuraBansApi api) {
        return API.compareAndSet(Objects.requireNonNull(api, "api"), null);
    }
}
