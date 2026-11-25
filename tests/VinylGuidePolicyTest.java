package com.halftonestudio;

public final class VinylGuidePolicyTest {
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    public static void main(String[] args){
        check(VinylGuidePolicy.shouldAutoShow(false,false,false),"First install must show guide");
        check(!VinylGuidePolicy.shouldAutoShow(true,false,false),"Seen guide must not reopen");
        check(!VinylGuidePolicy.shouldAutoShow(false,true,false),"Existing local user data must suppress auto guide");
        check(!VinylGuidePolicy.shouldAutoShow(false,false,true),"Upgrade without prior launch must suppress guide");
        check(!VinylGuidePolicy.mayShowTip(false,true,false),"No tip before completion");
        check(!VinylGuidePolicy.mayShowTip(true,false,false),"Disabled tips stay disabled");
        check(!VinylGuidePolicy.mayShowTip(true,true,true),"Shown tip must not repeat");
        check(VinylGuidePolicy.mayShowTip(true,true,false),"First enabled contextual tip shows");
        check(VinylGuidePolicy.suggestedPage(false,-1,0)==0,"No context starts at page 1");
        check(VinylGuidePolicy.suggestedPage(false,3,0)==3,"Colors opens the colors step");
        check(VinylGuidePolicy.suggestedPage(false,5,0)==4,"Export opens the export step");
        check(VinylGuidePolicy.suggestedPage(true,5,OutputDeviceProfile.CRICUT)==7,"Cricut export opens Cricut topic");
        check(VinylGuidePolicy.suggestedPage(true,5,OutputDeviceProfile.SILHOUETTE_BASIC)==8,"Silhouette export opens Silhouette topic");
        for(int w=-1;w<7;w++){
            check(VinylGuidePolicy.suggestedPage(false,w,0)<VinylGuidePolicy.GUIDE_PAGES,"guide page in range");
            check(VinylGuidePolicy.suggestedPage(true,w,1)<VinylGuidePolicy.TIP_PAGES,"tip page in range");
        }
        for(int p=0;p<VinylGuidePolicy.TIP_PAGES;p++)check(VinylGuidePolicy.workspaceForPage(true,p)<6,"tip shortcut in range");
        check(VinylGuidePolicy.workspaceForPage(false,0)==-1,"Overview has no shortcut");
        check(VinylGuidePolicy.workspaceForPage(true,VinylGuidePolicy.TIP_PAGES)==-1,"Out-of-range page has no shortcut");
        check(VinylGuidePolicy.mayOfferShortcut(0,false),"Prepare is always reachable");
        check(!VinylGuidePolicy.mayOfferShortcut(3,false),"No Colors shortcut without a photo");
        check(VinylGuidePolicy.mayOfferShortcut(3,true),"Colors shortcut with a photo");
        check(!VinylGuidePolicy.mayOfferShortcut(-1,true),"No shortcut for -1");
        check(VinylGuidePolicy.suggestedPage(true,1,0)==5,"Styles opens style tips");
        check(VinylGuidePolicy.workspaceForPage(true,0)==-1,"Portrait troubleshooting spans workspaces");
        check(!VinylGuidePolicy.mayOfferShortcut(6,true),"Invalid workspace rejected");
        check(!VinylGuidePolicy.mayOfferShortcut(Integer.MAX_VALUE,true),"Overflow workspace rejected");
        check(VinylGuidePolicy.suggestedPage(true,5,-1)==7,"Invalid output uses Cricut fallback");
        for(int w=0;w<6;w++)for(int d=0;d<8;d++){
            int page=VinylGuidePolicy.suggestedPage(true,w,d);
            check(page>=0&&page<VinylGuidePolicy.TIP_PAGES,"Valid contextual page");
        }
        System.out.println("PASS FirstTimeGuidePolicy: fresh installs, upgrades, state, tips, contextual routing");
    }
}
