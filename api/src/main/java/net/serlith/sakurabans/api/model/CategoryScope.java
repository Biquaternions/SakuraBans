package net.serlith.sakurabans.api.model;

/**
 * The scope of every server in a category (a configured group of servers).
 */
public non-sealed interface CategoryScope extends Scope {

    /**
     * The category.
     *
     * @return the category name, lower case
     */
    String category();

    /**
     * Creates the scope.
     *
     * @param category the category name: 1-64 characters of {@code a-z}, {@code 0-9}, {@code _},
     *         {@code -}, in any case
     * @return the scope
     */
    static CategoryScope of(final String category) {
        record Impl(String category) implements CategoryScope {
            Impl {
                category = Checks.identifier(category, "category");
            }
        }

        return new Impl(category);
    }
}
