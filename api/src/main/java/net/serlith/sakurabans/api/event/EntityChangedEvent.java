package net.serlith.sakurabans.api.event;

import java.util.Locale;
import java.util.Optional;
import net.serlith.sakurabans.api.kind.Kind;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.template.TemplateInfo;

/**
 * After an administrative entity was created, changed or deleted, so UIs can refresh and caches
 * can invalidate. Subscribe to this type for every entity, or to one subtype.
 *
 * <p>Runs on a SakuraBans worker thread.
 */
public sealed interface EntityChangedEvent extends SyncedEvent permits
        EntityChangedEvent.KindChanged, EntityChangedEvent.TemplateChanged,
        EntityChangedEvent.SettingsChanged, EntityChangedEvent.MessagesChanged {

    /**
     * How it changed.
     *
     * @return the change
     */
    ChangeType change();

    /**
     * Which entity, as in the audit log (see {@link net.serlith.sakurabans.api.audit.SubjectType}).
     *
     * @return the entity's id
     */
    String entityId();

    /**
     * Its version after the change (the last version for a deletion).
     *
     * @return the version
     */
    long version();

    /**
     * Who changed it.
     *
     * @return the actor
     */
    Actor actor();

    /**
     * A punishment kind changed.
     */
    non-sealed interface KindChanged extends EntityChangedEvent {

        /**
         * The kind as it is now.
         *
         * @return the kind, or empty if deleted
         */
        Optional<Kind> kind();
    }

    /**
     * A template changed.
     */
    non-sealed interface TemplateChanged extends EntityChangedEvent {

        /**
         * The template as it is now.
         *
         * @return the template, or empty if deleted
         */
        Optional<TemplateInfo> template();
    }

    /**
     * Network-wide settings changed. The entity id is {@code settings}.
     */
    non-sealed interface SettingsChanged extends EntityChangedEvent {
    }

    /**
     * Message templates of a locale changed. The entity id is the locale tag.
     */
    non-sealed interface MessagesChanged extends EntityChangedEvent {

        /**
         * The locale.
         *
         * @return the locale
         */
        Locale locale();
    }
}
