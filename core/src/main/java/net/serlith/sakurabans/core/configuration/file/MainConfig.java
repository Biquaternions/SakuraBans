package net.serlith.sakurabans.core.configuration.file;

import lombok.Getter;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

@Getter
@ConfigSerializable
public final class MainConfig {

    private ServerSection server = new ServerSection();
    private ScopesSection scopes = new ScopesSection();
    private WebSection web = new WebSection();
    private EnforcementSection enforcement = new EnforcementSection();
    private AltsSection alts = new AltsSection();
    private ReasonsSection reasons = new ReasonsSection();
    private ExemptionSection exemption = new ExemptionSection();
    private WarningsSection warnings = new WarningsSection();
    private DisplaySection display = new DisplaySection();
    private LockdownSection lockdown = new LockdownSection();
    private NotificationsSection notifications = new NotificationsSection();
    private PlayersSection players = new PlayersSection();
    private AuditSection audit = new AuditSection();
    private PlaceholdersSection placeholders = new PlaceholdersSection();
    private GeoIpSection geoip = new GeoIpSection();
    private CommandsSection commands = new CommandsSection();
}
