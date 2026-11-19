package net.serlith.sakurabans.api.model;

/**
 * Who issued, revoked or edited a punishment: {@link ConsoleOperator#INSTANCE}, a
 * {@link PlayerOperator} or a {@link CustomOperator}.
 */
public sealed interface Operator permits ConsoleOperator, PlayerOperator, CustomOperator {
}
