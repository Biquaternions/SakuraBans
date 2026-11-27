package net.serlith.sakurabans.core.storage;

import java.sql.Blob;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import net.serlith.sakurabans.core.storage.converter.BytesBinding;
import net.serlith.sakurabans.core.storage.dbo.SakuraTables;
import net.serlith.sakurabans.core.storage.dbo.SbTable;
import org.assertj.core.api.Assertions;
import org.jooq.Converter;
import org.jooq.DSLContext;
import org.jooq.ExecuteContext;
import org.jooq.ExecuteListener;
import org.jooq.Field;
import org.jooq.impl.DSL;
import org.jooq.tools.jdbc.DefaultPreparedStatement;
import org.junit.jupiter.api.Test;

/** Every binary column binds through {@link BytesBinding}, so H2 never gets a temporary LOB. */
class BinaryColumnsTest {

    private static List<Field<?>> binaryFields() {
        H2Database.create();
        final List<Field<?>> binary = new ArrayList<>();
        for (final SbTable<?> table : SakuraTables.all()) {
            for (final Field<?> field : table.fields()) {
                if (field.getDataType().getSQLDataType().isBinary()) {
                    binary.add(field);
                }
            }
        }
        return binary;
    }

    @Test
    void everyBinaryColumnUsesTheBytesBinding() {
        final List<Field<?>> binary = binaryFields();

        Assertions.assertThat(binary).extracting(Field::getName)
                .contains("uuid", "address", "address_hash", "secret", "payload");
        Assertions.assertThat(binary).allSatisfy(field -> Assertions.assertThat(
                field.getDataType().getBinding()).as(field.toString())
                .isInstanceOf(BytesBinding.class));
    }

    @Test
    void parametersAreBoundAsBytesOnH2() {
        final List<String> calls = new ArrayList<>();
        final DSLContext dsl = H2Database.create().configuration()
                .derive(new ExecuteListener() {
                    @Override
                    public void prepareEnd(final ExecuteContext ctx) {
                        ctx.statement(new Recording(ctx.statement(), calls));
                    }
                }).dsl();

        for (final Field<?> field : binaryFields()) {
            calls.clear();
            roundTrips(dsl, field);
            Assertions.assertThat(calls).as(field.toString()).containsExactly("setBytes");
        }
    }

    private static <T> void roundTrips(final DSLContext dsl, final Field<T> field) {
        @SuppressWarnings("unchecked")
        final Converter<byte[], T> converter =
                (Converter<byte[], T>) field.getDataType().getConverter();
        final byte[] bytes = new byte[field.getDataType().length()];
        bytes[0] = 1;
        final T value = converter.from(bytes);

        Assertions.assertThat(dsl.select(DSL.val(value, field)).fetchOne(0, field.getType()))
                .as(field.toString()).isEqualTo(value);
    }

    private static final class Recording extends DefaultPreparedStatement {

        private final List<String> calls;

        Recording(final PreparedStatement delegate, final List<String> calls) {
            super(delegate);
            this.calls = calls;
        }

        @Override
        public void setBytes(final int index, final byte[] value) throws SQLException {
            this.calls.add("setBytes");
            super.setBytes(index, value);
        }

        @Override
        public void setBlob(final int index, final Blob value) throws SQLException {
            this.calls.add("setBlob");
            super.setBlob(index, value);
        }
    }
}
