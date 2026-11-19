/**
 * Value types of the SakuraBans API: punishments, victims, operators and scopes.
 *
 * <p>Every type is an interface. Types you pass into the API (victims, operators, scopes) have a
 * static {@code of} factory that validates and normalises its arguments; it throws
 * {@link NullPointerException} for a missing value and {@link IllegalArgumentException} for an
 * invalid one. You may also implement them yourself; SakuraBans re-creates every value it
 * receives through {@code of}, so your implementation is validated the same way. Types that
 * SakuraBans hands out (punishments, history) are implemented by SakuraBans only; don't
 * implement them.
 */
@NullMarked
package net.serlith.sakurabans.api.model;

import org.jspecify.annotations.NullMarked;
