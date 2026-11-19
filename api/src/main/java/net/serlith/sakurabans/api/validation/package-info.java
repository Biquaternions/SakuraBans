/**
 * Validation errors shared by every builder and factory of the API.
 *
 * <p>Builders have {@code validate()}, which returns every problem as a {@link
 * net.serlith.sakurabans.api.validation.FieldError}, and {@code build()}, which throws a
 * {@link net.serlith.sakurabans.api.validation.ValidationException} carrying the same list. The
 * {@code of} factories throw it for their arguments. A missing required value is a programming
 * error and throws {@link NullPointerException} instead.
 */
@NullMarked
package net.serlith.sakurabans.api.validation;

import org.jspecify.annotations.NullMarked;
