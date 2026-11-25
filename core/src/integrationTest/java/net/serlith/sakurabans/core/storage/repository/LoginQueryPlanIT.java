package net.serlith.sakurabans.core.storage.repository;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.model.AddressStrictness;
import net.serlith.sakurabans.api.model.AddressVictim;
import net.serlith.sakurabans.api.model.ConsoleOperator;
import net.serlith.sakurabans.api.model.GlobalScope;
import net.serlith.sakurabans.api.model.PlayerVictim;
import net.serlith.sakurabans.api.model.PunishmentType;
import net.serlith.sakurabans.api.model.ServerScope;
import net.serlith.sakurabans.api.model.Victim;
import net.serlith.sakurabans.api.model.Visibility;
import net.serlith.sakurabans.api.punish.OverridePolicy;
import net.serlith.sakurabans.api.punish.PunishmentQuery.Applicability;
import net.serlith.sakurabans.core.storage.ContainerDatabase;
import net.serlith.sakurabans.core.storage.DatabaseTest;
import net.serlith.sakurabans.core.storage.TestDatabase;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The login query (DESIGN 7.4; {@code LoginGate}, S19) must reach every table through an
 * index. PostgreSQL is asked with sequential scans disabled, so any "Seq Scan" left means no
 * index could serve that access; MariaDB and MySQL must list a usable key for each table.
 */
@ParameterizedClass
@EnumSource(ContainerDatabase.class)
class LoginQueryPlanIT extends DatabaseTest {

    @Parameter
    private ContainerDatabase container;

    @Override
    protected TestDatabase database() {
        return this.container;
    }

    @Test
    void theLoginQueryUsesIndexes() throws Exception {
        final UUID player = this.seed();
        final InetAddress address = address(7);

        final String sql = this.read(dsl -> dsl.renderInlined(
                PunishmentRepository.activeApplicableQuery(dsl, PunishmentType.BAN,
                        Applicability.of(player, address, AddressStrictness.NORMAL),
                        List.of(GlobalScope.INSTANCE, ServerScope.of("lobby")),
                        this.now.get(), AltPolicy.NONE, Set.of(AddressStrictness.STRICT))));
        final List<String> plan = this.read(dsl -> dsl.connectionResult(connection ->
                this.container == ContainerDatabase.POSTGRESQL
                        ? postgresPlan(connection, sql)
                        : mysqlPlan(connection, sql)));

        Assertions.assertThat(String.join("\n", plan)).as("the plan reaches the tables")
                .contains("sb_active_punishments", "sb_victims");
        Assertions.assertThat(plan).as(sql).noneMatch(line -> line.startsWith("PROBLEM"));
    }

    /** A few hundred rows, so the planner has something to choose between. */
    private UUID seed() {
        final PlayerRepository players = new PlayerRepository(this.storage);
        final PunishmentRepository punishments = new PunishmentRepository(this.storage,
                new PunishmentMapper(Long::toString));
        final Actor console = Actor.of(ConsoleOperator.INSTANCE, "console");
        UUID last = null;
        for (int i = 0; i < 200; i++) {
            final UUID uuid = UUID.randomUUID();
            last = uuid;
            players.recordConnection(uuid, "p" + i, this.now.get(), address(i % 50), null)
                    .join();
            final Victim victim = i % 3 == 0 ? AddressVictim.of(address(i % 50))
                    : PlayerVictim.of(uuid);
            punishments.insert(new NewPunishment(PunishmentType.BAN, "ban", false, null,
                    victim, i % 2 == 0 ? GlobalScope.INSTANCE : ServerScope.of("lobby"), "",
                    console, this.now.get(), null, null, Visibility.NORMAL, null, null,
                    List.of(), null), OverridePolicy.REPLACE).join();
        }
        return last;
    }

    private static InetAddress address(final int i) {
        try {
            return InetAddress.getByAddress(new byte[] {(byte) 198, 51, 100, (byte) i});
        } catch (final UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Every plan line; lines that read a table without an index start with "PROBLEM". */
    private static List<String> postgresPlan(final Connection connection, final String sql)
            throws SQLException {
        final List<String> lines = new ArrayList<>();
        try (Statement statement = connection.createStatement()) {
            statement.execute("ANALYZE");
            statement.execute("SET enable_seqscan = off");
            try (ResultSet plan = statement.executeQuery("EXPLAIN " + sql)) {
                while (plan.next()) {
                    final String line = plan.getString(1).strip();
                    lines.add(line.contains("Seq Scan") ? "PROBLEM " + line : line);
                }
            } finally {
                statement.execute("RESET enable_seqscan");
            }
        }
        return lines;
    }

    private static List<String> mysqlPlan(final Connection connection, final String sql)
            throws SQLException {
        final List<String> lines = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet plan = statement.executeQuery("EXPLAIN " + sql)) {
            while (plan.next()) {
                final String table = String.valueOf(plan.getString("table"));
                final String line = table + " type=" + plan.getString("type") + " keys="
                        + plan.getString("possible_keys");
                final boolean ours = table.toLowerCase(Locale.ROOT).startsWith("sb_");
                lines.add(ours && plan.getString("possible_keys") == null
                        ? "PROBLEM " + line : line);
            }
        }
        return lines;
    }
}
