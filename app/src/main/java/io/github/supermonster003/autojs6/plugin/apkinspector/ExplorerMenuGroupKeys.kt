package io.github.supermonster003.autojs6.plugin.apkinspector

/**
 * Catalog-level keys that name the plugin's first-level menu in the host's file and directory menus (AutoJs6 6.8.1+,
 * Explorer Action protocol v27 `menuGroupLabel`, host docs/dev/explorer-action-protocol-v27.md). They have the form of
 * an action label: a string resource of this plugin and the text used when the host cannot read it.
 *
 * The bundled explorer-action-api AAR predates `ExplorerActionCatalogKeys.MENU_GROUP_LABEL_RESOURCE_NAME` /
 * `MENU_GROUP_LABEL_FALLBACK`, so the key names are written out here. Hosts read them from catalogs of every protocol
 * version and older hosts ignore unknown catalog keys, so declaring them does not raise the catalog version.
 */
internal object ExplorerMenuGroupKeys {
    const val RESOURCE_NAME = "menuGroupLabelResourceName"
    const val FALLBACK = "menuGroupLabelFallback"
}
