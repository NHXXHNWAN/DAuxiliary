# Miuix example provenance

The seven Kotlin files under `app/src/main/kotlin/com/dauxiliary/ui/miuix/component/{liquid,animation}` come from the official compose-miuix-ui/miuix example, tag `v0.9.4-rc01`, commit `4a6b750b578880146876e4ab77097d9b01702413`.

Source: https://github.com/compose-miuix-ui/miuix/tree/4a6b750b578880146876e4ab77097d9b01702413/example/shared/src/commonMain/kotlin/component

Local adaptations: package/import namespace; example `ui.isInDarkTheme()` replaced with Compose `isSystemInDarkTheme()` to match AppTheme's current system-following behavior. Upstream visual constants and shaders are retained. Preserve upstream Apache-2.0 notices.

The iOS-like implementation is an official example component, not a public miuix-ui API. Its local source location does not make it an independently designed component.

Backdrop integration follows CompactScreenLayout in the same revision's AppContent.kt: record a theme surface before content, and attach layerBackdrop to a full-size host outside content padding. Device validation of rendering is still required.
