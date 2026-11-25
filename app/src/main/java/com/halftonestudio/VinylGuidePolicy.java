package com.halftonestudio;

/** Pure, host-testable first-run and guide-routing rules. Existing installations are not treated as new users. */
public final class VinylGuidePolicy {
    private VinylGuidePolicy() {}
    /** New User Guide pages, then Tips &amp; Tricks topics, in resource order. */
    public static final int GUIDE_PAGES=5, TIP_PAGES=11;
    // Workspace indices match StudioActivity.WORKSPACE_LABELS: Prepare, Styles, Adjust, Colors, Layers, Export.
    private static final int[] GUIDE_PAGE_FOR_WORKSPACE={1,2,2,3,3,4};
    private static final int[] TIP_FOR_WORKSPACE={2,5,3,1,0,7};
    private static final int[] WORKSPACE_FOR_GUIDE_PAGE={-1,0,1,3,5};
    private static final int[] WORKSPACE_FOR_TIP={-1,3,0,2,2,2,5,5,5,-1,-1};
    private static final int TIP_CRICUT=7, TIP_OTHER_CUTTERS=8;

    public static boolean shouldAutoShow(boolean stateRecorded, boolean priorUserState, boolean updatedInstall) {
        return !stateRecorded && !priorUserState && !updatedInstall;
    }
    public static boolean mayShowTip(boolean guideSeen, boolean tipsEnabled, boolean tipShown) {
        return guideSeen && tipsEnabled && !tipShown;
    }
    /** Page (within the chosen section) most relevant to the current workspace; 0 when there is no context. */
    public static int suggestedPage(boolean tips, int workspace, int outputDevice) {
        if(workspace<0||workspace>=GUIDE_PAGE_FOR_WORKSPACE.length)return 0;
        if(!tips)return GUIDE_PAGE_FOR_WORKSPACE[workspace];
        if(workspace==5)return OutputDeviceProfile.byId(outputDevice).id==OutputDeviceProfile.CRICUT?TIP_CRICUT:TIP_OTHER_CUTTERS;
        return TIP_FOR_WORKSPACE[workspace];
    }
    /** Workspace a page's "Open …" shortcut leads to, or -1 when the page has no single destination. */
    public static int workspaceForPage(boolean tips, int page) {
        int[] map=tips?WORKSPACE_FOR_TIP:WORKSPACE_FOR_GUIDE_PAGE;
        return page<0||page>=map.length?-1:map[page];
    }
    /** Every workspace but Prepare needs a photo; never offer a shortcut that would only show an error. */
    public static boolean mayOfferShortcut(int workspace, boolean hasPhoto) {
        return workspace>=0&&workspace<GUIDE_PAGE_FOR_WORKSPACE.length&&(workspace==0||hasPhoto);
    }
}
