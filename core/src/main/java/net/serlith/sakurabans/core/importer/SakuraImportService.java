package net.serlith.sakurabans.core.importer;

import java.util.concurrent.CompletableFuture;
import net.serlith.sakurabans.api.admin.AdminResult;
import net.serlith.sakurabans.api.auth.Action;
import net.serlith.sakurabans.api.imports.ImportOptions;
import net.serlith.sakurabans.api.imports.ImportReport;
import net.serlith.sakurabans.api.imports.ImportService;
import net.serlith.sakurabans.api.imports.ImportedPunishment;
import net.serlith.sakurabans.api.model.Actor;
import net.serlith.sakurabans.api.validation.FieldError;
import net.serlith.sakurabans.api.validation.ValidationException;
import net.serlith.sakurabans.core.model.ApiValues;
import net.serlith.sakurabans.core.model.SakuraAdminResult;
import net.serlith.sakurabans.core.punish.ActionCheck;

public final class SakuraImportService implements ImportService {

    private final ImportJobs jobs;
    private final ActionCheck actions;

    public SakuraImportService(final ImportJobs jobs, final ActionCheck actions) {
        this.jobs = jobs;
        this.actions = actions;
    }

    @Override
    public CompletableFuture<AdminResult<ImportReport>> importPunishments(
            final Iterable<ImportedPunishment> punishments, final ImportOptions options,
            final Actor actor) {
        final Actor checked;
        final ImportOptions copy;
        try {
            checked = ApiValues.actor(actor);
            copy = ApiValues.importOptions(options);
            // Every imported punishment is stored with this source.
            Actor.of(checked.operator(), copy.source());
        } catch (final ValidationException e) {
            return CompletableFuture.completedFuture(
                    new SakuraAdminResult.ValidationFailed<>(e.errors()));
        } catch (final RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
        return this.actions.check(checked, Action.Manage.of(Action.Manage.Area.IMPORT))
                .thenCompose(decision -> {
                    if (!decision.allowed()) {
                        return CompletableFuture.completedFuture(
                                new SakuraAdminResult.Forbidden<>(decision));
                    }
                    return this.jobs.run(punishments, copy, checked).thenApply(outcome ->
                            switch (outcome) {
                                case ImportOutcome.Done done ->
                                        new SakuraAdminResult.Ok<>((ImportReport) done.report());
                                case ImportOutcome.Busy busy -> SakuraAdminResult.<ImportReport>
                                        invalid("import", FieldError.Code.NOT_ALLOWED,
                                                "another import is running");
                                default -> throw new IllegalStateException(
                                        "unexpected outcome " + outcome);
                            });
                });
    }
}
