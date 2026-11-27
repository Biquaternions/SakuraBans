package net.serlith.sakurabans.core.storage.converter;

import java.sql.SQLException;
import org.jooq.Binding;
import org.jooq.BindingGetResultSetContext;
import org.jooq.BindingGetSQLInputContext;
import org.jooq.BindingGetStatementContext;
import org.jooq.BindingRegisterContext;
import org.jooq.BindingSQLContext;
import org.jooq.BindingSetSQLOutputContext;
import org.jooq.BindingSetStatementContext;
import org.jooq.ContextConverter;
import org.jooq.Converter;
import org.jooq.DataType;
import org.jooq.Formatter;

public final class BytesBinding<U> implements Binding<byte[], U> {

    private static final long serialVersionUID = 1L;

    private final Binding<byte[], U> defaults;
    private final ContextConverter<byte[], U> converter;

    @SuppressWarnings("unchecked")
    public BytesBinding(final DataType<byte[]> type, final Converter<byte[], U> converter) {
        this.defaults = (Binding<byte[], U>) type.asConvertedDataType(converter).getBinding();
        this.converter = ContextConverter.scoped(converter);
    }

    @Override
    public Converter<byte[], U> converter() {
        return this.converter;
    }

    @Override
    public Formatter formatter() {
        return this.defaults.formatter();
    }

    @Override
    public void sql(final BindingSQLContext<U> ctx) throws SQLException {
        this.defaults.sql(ctx);
    }

    @Override
    public void register(final BindingRegisterContext<U> ctx) throws SQLException {
        this.defaults.register(ctx);
    }

    @Override
    public void set(final BindingSetStatementContext<U> ctx) throws SQLException {
        final byte[] bytes = this.converter.to(ctx.value(), ctx.converterContext());
        if (bytes == null) {
            this.defaults.set(ctx);
        } else {
            ctx.statement().setBytes(ctx.index(), bytes);
        }
    }

    @Override
    public void set(final BindingSetSQLOutputContext<U> ctx) throws SQLException {
        this.defaults.set(ctx);
    }

    @Override
    public void get(final BindingGetResultSetContext<U> ctx) throws SQLException {
        ctx.value(this.converter.from(ctx.resultSet().getBytes(ctx.index()),
                ctx.converterContext()));
    }

    @Override
    public void get(final BindingGetStatementContext<U> ctx) throws SQLException {
        ctx.value(this.converter.from(ctx.statement().getBytes(ctx.index()),
                ctx.converterContext()));
    }

    @Override
    public void get(final BindingGetSQLInputContext<U> ctx) throws SQLException {
        this.defaults.get(ctx);
    }
}
