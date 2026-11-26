package net.serlith.sakurabans.api.data;

/**
 * The outcome of a {@link PlayerDataService} request. Implemented by SakuraBans; don't implement
 * it.
 *
 * @param <T> the type of the value on success
 */
public sealed interface PlayerDataResult<T>
        permits PlayerDataResult.Success, PlayerDataResult.Forbidden {

    /**
     * It was done.
     *
     * @param <T> the type of the value
     */
    non-sealed interface Success<T> extends PlayerDataResult<T> {

        /**
         * What it produced.
         *
         * @return the value
         */
        T value();
    }

    /**
     * The actor may not do this.
     *
     * @param <T> the type of the value on success
     */
    non-sealed interface Forbidden<T> extends PlayerDataResult<T> {
    }
}
