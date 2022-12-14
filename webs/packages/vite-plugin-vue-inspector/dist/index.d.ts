import { PluginOption } from 'vite';

interface VitePluginInspectorOptions {
    /**
    * Vue version
    * @default 3
    */
    vue?: 2 | 3;
    /**
    * Default enable state
    * @default false
    */
    enabled?: boolean;
    /**
    * Define a combo key to toggle inspector
    * @default 'control-shift' on windows, 'meta-shift' on other os
    *
    * any number of modifiers `control` `shift` `alt` `meta` followed by zero or one regular key, separated by -
    * examples: control-shift, control-o, control-alt-s  meta-x control-meta
    * Some keys have native behavior (e.g. alt-s opens history menu on firefox).
    * To avoid conflicts or accidentally typing into inputs, modifier only combinations are recommended.
    */
    toggleComboKey?: string;
    /**
    * Toggle button visibility
    * @default 'active'
    */
    toggleButtonVisibility?: "always" | "active" | "never";
    /**
    * Toggle button display position
    * @default top-right
    */
    toggleButtonPos?: "top-right" | "top-left" | "bottom-right" | "bottom-left";
    /**
    * append an import to the module id ending with `appendTo` instead of adding a script into body
    * useful for frameworks that do not support trannsformIndexHtml hook (e.g. Nuxt3)
    *
    * WARNING: only set this if you know exactly what it does.
    */
    appendTo?: string;
}
declare function VitePluginInspector(options?: VitePluginInspectorOptions): PluginOption;

export { VitePluginInspectorOptions, VitePluginInspector as default };
