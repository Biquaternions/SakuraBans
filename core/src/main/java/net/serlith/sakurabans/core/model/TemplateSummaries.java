package net.serlith.sakurabans.core.model;

import java.util.TreeMap;
import net.serlith.sakurabans.api.template.LadderTemplate;
import net.serlith.sakurabans.api.template.PresetTemplate;
import net.serlith.sakurabans.api.template.TemplateDisplay;
import net.serlith.sakurabans.api.template.TemplateInfo;
import net.serlith.sakurabans.core.util.Scopes;

public final class TemplateSummaries {

    private TemplateSummaries() {
    }

    /** Every field as one line: the audit log's old and new value, and change detection. */
    public static String summary(final TemplateInfo template) {
        return "kind=" + TemplateKind.of(template).key()
                + details(template)
                + " ip=" + template.ip()
                + " shadow=" + template.shadow()
                + " scope=" + template.scope().map(Scopes::format).orElse("default")
                + " reason=" + template.reason()
                + " permission-required=" + template.permissionRequired()
                + " actions=" + template.actions()
                + " groups=" + new TreeMap<>(template.groups())
                + template.message().map(text -> " message=" + text).orElse("")
                + (template.display() == TemplateDisplay.CHAT ? ""
                        : " display=" + TemplateDisplays.key(template.display()))
                + (template.silent() ? " silent=true" : "");
    }

    /** The kind's own fields, starting with a space. */
    private static String details(final TemplateInfo template) {
        return switch (template) {
            case PresetTemplate preset -> " duration="
                    + TemplateSteps.format(preset.duration()).orElse("open");
            case LadderTemplate ladder -> " steps=" + ladder.steps().stream()
                            .map(step -> TemplateSteps.format(step)
                                    + step.reason().map(text -> " reason=" + text).orElse("")
                                    + step.message().map(text -> " message=" + text).orElse("")
                                    + (step.actions().isEmpty() ? ""
                                            : step.actions().toString()))
                            .toList()
                    + " expiry=" + ladder.expiry().map(TemplateSteps::compact).orElse("none")
                    + " address-shared=" + ladder.addressShared();
        };
    }
}
