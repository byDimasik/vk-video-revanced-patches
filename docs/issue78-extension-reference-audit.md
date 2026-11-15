# #78 extension-reference audit (1.171 dexes)

Every external (app-owned) reference the *compiled* extension dexes make, audited against the shipped 1.171 dexes at patch time.

**194/194 resolve.**

## `Lapp/revanced/extension/sponsorskip/BehaviorStore;` (1/1)

| reference | resolves |
|---|---|
| `Lcom/vk/core/preference/Preference;->f(Ljava/lang/String;)Landroid/content/SharedPreferences;` | yes |

## `Lapp/revanced/extension/sponsorskip/Episodes;` (6/6)

| reference | resolves |
|---|---|
| `Lcom/vk/dto/common/VideoEpisode;` | yes |
| `Lcom/vk/dto/common/VideoEpisode;->a:I` | yes |
| `Lcom/vk/dto/common/VideoEpisode;->b:Ljava/lang/String;` | yes |
| `Lcom/vk/dto/common/VideoFile;` | yes |
| `Lcom/vk/dto/common/VideoFile;->k3()Ljava/util/List;` | yes |
| `[Lapp/revanced/extension/sponsorskip/Segment;` | yes |

## `Lapp/revanced/extension/sponsorskip/PlayedSections;` (3/3)

| reference | resolves |
|---|---|
| `Lb83/a;->getDuration()J` | yes |
| `Ldp7/a;` | yes |
| `Lnp7/b;` | yes |

## `Lapp/revanced/extension/sponsorskip/SeekbarSections;` (4/4)

| reference | resolves |
|---|---|
| `Ldp7/b;` | yes |
| `Ldp7/c;` | yes |
| `Lnp7/b;` | yes |
| `Lnp7/c;` | yes |

## `Lapp/revanced/extension/sponsorskip/settings/SponsorSkipBehaviorDialog;` (7/7)

| reference | resolves |
|---|---|
| `Lcom/vk/core/fragments/FragmentImpl;` | yes |
| `Lcom/vk/core/view/components/cell/VkCell;` | yes |
| `Lky1/f$a;` | yes |
| `Lky1/f$a;-><init>(Landroid/content/Context;)V` | yes |
| `Lky1/f$a;->o(Ljava/lang/CharSequence;)Lly1/b$a;` | yes |
| `Lky1/f$a;->v([Ljava/lang/CharSequence;ILandroid/content/DialogInterface$OnClickListener;)Lly1/b$a;` | yes |
| `Lly1/b$a;->create()Landroidx/appcompat/app/d;` | yes |

## `Lapp/revanced/extension/sponsorskip/settings/SponsorSkipFragment;` (17/17)

| reference | resolves |
|---|---|
| `Lcom/vk/core/fragments/FragmentImpl;-><init>()V` | yes |
| `Lcom/vk/core/fragments/FragmentImpl;->onViewCreated(Landroid/view/View;Landroid/os/Bundle;)V` | yes |
| `Lcom/vk/core/view/AppBarShadowView;` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar$Middle$Text$Title;` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar$Middle$Text$Title;-><init>(Ljava/lang/String;La52/c0;Lcom/vk/core/view/components/topbar/VkTopBar$Middle$Text$Title$a$a;Lcom/vk/core/view/components/topbar/VkTopBar$Middle$Text$Title$Size;I)V` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar$Middle$Text;` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar$Middle$Text;-><init>(Lcom/vk/core/view/components/topbar/VkTopBar$Middle$Text$Title;Lcom/vk/core/view/components/topbar/VkTopBar$Middle$Text$c;Lcom/vk/core/view/components/topbar/VkTopBar$Middle$Text$a$b;Lcom/vk/core/compose/component/semantics/SemanticsConfiguration;I)V` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar$c;` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar$c;-><init>(Lkotlin/jvm/functions/Function0;Ltx1/a;Lv72/e;Lcom/vk/core/compose/component/semantics/a;I)V` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar;` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar;-><init>(Landroid/content/Context;Landroid/util/AttributeSet;II)V` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar;->setBack(Lcom/vk/core/view/components/topbar/VkTopBar$c;)V` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar;->setBefore(Lcom/vk/core/view/components/topbar/VkTopBar$d;)V` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar;->setMiddle(Lcom/vk/core/view/components/topbar/VkTopBar$Middle;)V` | yes |
| `Ltx1/a;->Companion:Ltx1/a$a;` | yes |
| `Lv57/a;->f(Lcom/vk/core/fragments/FragmentImpl;Lcom/vk/core/view/components/topbar/VkTopBar;)Z` | yes |
| `Lxy1/j0;->m(Landroid/view/View;)V` | yes |

## `Lapp/revanced/extension/sponsorskip/settings/SponsorSkipGroupClick;` (4/4)

| reference | resolves |
|---|---|
| `La64/x;-><init>(Ljava/lang/Class;Ljava/lang/Class;Landroid/os/Bundle;)V` | yes |
| `La64/x;->t(Lcom/vk/core/fragments/FragmentImpl;)V` | yes |
| `Lcom/vk/core/fragments/FragmentImpl;` | yes |
| `Lcom/vk/video/screens/settings/main/ui/VideoUserSettingsFragment$a;` | yes |

## `Lapp/revanced/extension/sponsorskip/settings/SponsorSkipRootRow;` (23/23)

| reference | resolves |
|---|---|
| `Lcom/vk/core/fragments/FragmentImpl;` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Left$Main$Size;->Medium:Lcom/vk/core/view/components/cell/VkCell$Left$Main$Size;` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Left$Main$c;` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Left$Main$c;-><init>(Lv72/g;Lcom/vk/core/view/components/cell/VkCell$Left$Main$Size;Lv72/r$a;I)V` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Left$b;` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Left$b;-><init>(Lcom/vk/core/view/components/cell/VkCell$Left$Main;Lcom/vk/core/view/components/cell/VkCell$Left$c;)V` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Middle$a;->a(Lcom/vk/core/view/components/cell/VkCell$Middle$a;Lcom/vk/core/view/components/cell/VkCell$Middle$e;Lcom/vk/core/view/components/cell/VkCell$Middle$d;Lcom/vk/core/view/components/cell/VkCell$Middle$c;I)Lcom/vk/core/view/components/cell/VkCell$Middle$b;` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Middle$e;` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Middle$e;-><init>(Ltx1/a;ILandroid/text/TextUtils$TruncateAt;Lcom/vk/core/view/components/cell/VkCell$Middle$e$b$b;Lcom/vk/core/view/components/cell/VkCell$Middle$e$b;I)V` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Middle;->Companion:Lcom/vk/core/view/components/cell/VkCell$Middle$a;` | yes |
| `Lcom/vk/core/view/components/cell/VkCell;` | yes |
| `Lcom/vk/core/view/components/cell/VkCell;-><init>(Landroid/content/Context;Landroid/util/AttributeSet;II)V` | yes |
| `Lcom/vk/core/view/components/cell/VkCell;->setLeft(Lcom/vk/core/view/components/cell/VkCell$Left$b;)V` | yes |
| `Lcom/vk/core/view/components/cell/VkCell;->setMiddle(Lcom/vk/core/view/components/cell/VkCell$Middle$b;)V` | yes |
| `Lcom/vk/core/view/components/cell/VkCell;->setRippleEnabled(Z)V` | yes |
| `Ltx1/a$h;` | yes |
| `Ltx1/a$h;-><init>(Ljava/lang/CharSequence;)V` | yes |
| `Lv72/b;` | yes |
| `Lv72/b;-><init>(I)V` | yes |
| `Lv72/j;` | yes |
| `Lv72/j;-><init>(I)V` | yes |
| `Lv72/r$a;` | yes |
| `Lv72/r$a;-><init>(Lv72/q;)V` | yes |

## `Lapp/revanced/extension/sponsorskip/settings/SponsorSkipRows;` (18/18)

| reference | resolves |
|---|---|
| `Lcom/vk/core/view/components/cell/VkCell$Middle$a;->a(Lcom/vk/core/view/components/cell/VkCell$Middle$a;Lcom/vk/core/view/components/cell/VkCell$Middle$e;Lcom/vk/core/view/components/cell/VkCell$Middle$d;Lcom/vk/core/view/components/cell/VkCell$Middle$c;I)Lcom/vk/core/view/components/cell/VkCell$Middle$b;` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Middle$b;` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Middle$d;` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Middle$d;-><init>(Ltx1/a;Landroidx/compose/foundation/pager/y;ILandroid/text/TextUtils$TruncateAt;Lcom/vk/core/view/components/cell/VkCell$Middle$d$b$b;I)V` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Middle$e;` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Middle$e;-><init>(Ltx1/a;ILandroid/text/TextUtils$TruncateAt;Lcom/vk/core/view/components/cell/VkCell$Middle$e$b$b;Lcom/vk/core/view/components/cell/VkCell$Middle$e$b;I)V` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Middle;->Companion:Lcom/vk/core/view/components/cell/VkCell$Middle$a;` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Right$b;` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Right$b;-><init>(Lkotlin/jvm/functions/Function1;Lv72/r$a;I)V` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Right$d;` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Right$d;-><init>(Lcom/vk/core/view/components/cell/VkCell$Right$a;Lcom/vk/core/view/components/cell/VkCell$Right$e;Lcom/vk/core/view/components/cell/VkCell$Right$ExtraAction$a;Lcom/vk/core/view/components/cell/VkCell$Right$b;I)V` | yes |
| `Lcom/vk/core/view/components/cell/VkCell;` | yes |
| `Lcom/vk/core/view/components/cell/VkCell;-><init>(Landroid/content/Context;Landroid/util/AttributeSet;II)V` | yes |
| `Lcom/vk/core/view/components/cell/VkCell;->setMiddle(Lcom/vk/core/view/components/cell/VkCell$Middle$b;)V` | yes |
| `Lcom/vk/core/view/components/cell/VkCell;->setRight(Lcom/vk/core/view/components/cell/VkCell$Right$d;)V` | yes |
| `Lcom/vk/core/view/components/cell/VkCell;->setRippleEnabled(Z)V` | yes |
| `Ltx1/a$h;` | yes |
| `Ltx1/a$h;-><init>(Ljava/lang/CharSequence;)V` | yes |

## `Lapp/revanced/extension/sponsorskip/settings/SponsorSkipSettings;` (11/11)

| reference | resolves |
|---|---|
| `Lcom/vk/core/fragments/FragmentImpl;` | yes |
| `Lcom/vk/core/view/components/cell/VkCell;` | yes |
| `Lcom/vk/core/view/components/cell/VkCell;->setMiddle(Lcom/vk/core/view/components/cell/VkCell$Middle$b;)V` | yes |
| `Lcom/vk/core/view/components/switch/VkSwitchItem;` | yes |
| `Lcom/vk/core/view/components/switch/VkSwitchItem;-><init>(Landroid/content/Context;Landroid/util/AttributeSet;)V` | yes |
| `Lcom/vk/core/view/components/switch/VkSwitchItem;->setAlignSwitchToEnd(Z)V` | yes |
| `Lcom/vk/core/view/components/switch/VkSwitchItem;->setChecked(Z)V` | yes |
| `Lcom/vk/core/view/components/switch/VkSwitchItem;->setListener(Lcom/vk/core/view/components/switch/VkSwitchItem$a;)V` | yes |
| `Lcom/vk/core/view/components/switch/VkSwitchItem;->setTitle(Ltx1/a;)V` | yes |
| `Ltx1/a$h;` | yes |
| `Ltx1/a$h;-><init>(Ljava/lang/CharSequence;)V` | yes |

## `Lapp/revanced/extension/sponsorskip/SkipButton;` (2/2)

| reference | resolves |
|---|---|
| `Lcom/vk/libvideo/autoplay/VideoAutoPlay;` | yes |
| `Lcom/vk/libvideo/autoplay/VideoAutoPlay;->K:Ljava/lang/ref/WeakReference;` | yes |

## `Lapp/revanced/extension/sponsorskip/SkipEngine$State;` (2/2)

| reference | resolves |
|---|---|
| `Lb83/a;` | yes |
| `[Lapp/revanced/extension/sponsorskip/Segment;` | yes |

## `Lapp/revanced/extension/sponsorskip/SkipEngine;` (12/12)

| reference | resolves |
|---|---|
| `Lb83/a;` | yes |
| `Lb83/a;->getDuration()J` | yes |
| `Lb83/a;->getPosition()J` | yes |
| `Lb83/a;->isPlaying()Z` | yes |
| `Lb83/a;->t(J)V` | yes |
| `Lcom/vk/dto/common/VideoFile;` | yes |
| `Lcom/vk/dto/common/VideoFile;->O0()Lcom/vk/dto/common/id/UserId;` | yes |
| `Lcom/vk/dto/common/VideoFile;->getDuration()I` | yes |
| `Lcom/vk/dto/common/VideoFile;->k3()Ljava/util/List;` | yes |
| `Lcom/vk/dto/common/id/UserId;->c()J` | yes |
| `Lcom/vk/libvideo/autoplay/VideoAutoPlay;` | yes |
| `Lcom/vk/libvideo/autoplay/VideoAutoPlay;->K:Ljava/lang/ref/WeakReference;` | yes |

## `Lapp/revanced/extension/vkvideo/AdsSettings;` (41/41)

| reference | resolves |
|---|---|
| `Lcom/vk/core/fragments/FragmentImpl;` | yes |
| `Lcom/vk/core/preference/Preference;->f(Ljava/lang/String;)Landroid/content/SharedPreferences;` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Left$Main$Size;->Medium:Lcom/vk/core/view/components/cell/VkCell$Left$Main$Size;` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Left$Main$c;` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Left$Main$c;-><init>(Lv72/g;Lcom/vk/core/view/components/cell/VkCell$Left$Main$Size;Lv72/r;)V` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Left$b;` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Left$b;-><init>(Lcom/vk/core/view/components/cell/VkCell$Left$Main;Lcom/vk/core/view/components/cell/VkCell$Left$c;)V` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Middle$a;->a(Lcom/vk/core/view/components/cell/VkCell$Middle$a;Lcom/vk/core/view/components/cell/VkCell$Middle$e;Lcom/vk/core/view/components/cell/VkCell$Middle$d;Lcom/vk/core/view/components/cell/VkCell$Middle$c;I)Lcom/vk/core/view/components/cell/VkCell$Middle$b;` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Middle$e;` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Middle$e;-><init>(Ltx1/a;ILandroid/text/TextUtils$TruncateAt;Lcom/vk/core/view/components/cell/VkCell$Middle$e$b$b;Lcom/vk/core/view/components/cell/VkCell$Middle$e$b;I)V` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Middle;->Companion:Lcom/vk/core/view/components/cell/VkCell$Middle$a;` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Right$b;` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Right$b;-><init>(Lkotlin/jvm/functions/Function1;Lv72/r$a;I)V` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Right$d;` | yes |
| `Lcom/vk/core/view/components/cell/VkCell$Right$d;-><init>(Lcom/vk/core/view/components/cell/VkCell$Right$a;Lcom/vk/core/view/components/cell/VkCell$Right$e;Lcom/vk/core/view/components/cell/VkCell$Right$ExtraAction$a;Lcom/vk/core/view/components/cell/VkCell$Right$b;I)V` | yes |
| `Lcom/vk/core/view/components/cell/VkCell;` | yes |
| `Lcom/vk/core/view/components/cell/VkCell;-><init>(Landroid/content/Context;Landroid/util/AttributeSet;II)V` | yes |
| `Lcom/vk/core/view/components/cell/VkCell;->setLeft(Lcom/vk/core/view/components/cell/VkCell$Left$b;)V` | yes |
| `Lcom/vk/core/view/components/cell/VkCell;->setMiddle(Lcom/vk/core/view/components/cell/VkCell$Middle$b;)V` | yes |
| `Lcom/vk/core/view/components/cell/VkCell;->setRight(Lcom/vk/core/view/components/cell/VkCell$Right$d;)V` | yes |
| `Lcom/vk/core/view/components/cell/VkCell;->setRippleEnabled(Z)V` | yes |
| `Lcom/vk/core/view/components/switch/VkSwitchItem;` | yes |
| `Lcom/vk/core/view/components/switch/VkSwitchItem;-><init>(Landroid/content/Context;Landroid/util/AttributeSet;)V` | yes |
| `Lcom/vk/core/view/components/switch/VkSwitchItem;->setAlignSwitchToEnd(Z)V` | yes |
| `Lcom/vk/core/view/components/switch/VkSwitchItem;->setChecked(Z)V` | yes |
| `Lcom/vk/core/view/components/switch/VkSwitchItem;->setListener(Lcom/vk/core/view/components/switch/VkSwitchItem$a;)V` | yes |
| `Lcom/vk/core/view/components/switch/VkSwitchItem;->setTitle(Ltx1/a;)V` | yes |
| `Lcom/vk/video/screens/profile/adapter/ProfileMenuItemType;->ITEM_WITH_ICON:Lcom/vk/video/screens/profile/adapter/ProfileMenuItemType;` | yes |
| `Lcom/vk/video/screens/profile/adapter/ProfileMenuItemType;->ordinal()I` | yes |
| `Lcom/vk/video/screens/profile/adapter/holder/p$a;` | yes |
| `Lcom/vk/video/screens/profile/adapter/holder/p$a;-><init>(ILjava/lang/String;Lkotlin/jvm/functions/Function1;Z)V` | yes |
| `Ltx1/a$h;` | yes |
| `Ltx1/a$h;-><init>(Ljava/lang/CharSequence;)V` | yes |
| `Lv72/b;` | yes |
| `Lv72/b;-><init>(I)V` | yes |
| `Lv72/j;` | yes |
| `Lv72/j;-><init>(I)V` | yes |
| `Lv72/r$a;` | yes |
| `Lv72/r$a;-><init>(Lv72/q;)V` | yes |
| `Ly57/e$a;` | yes |
| `Ly57/e$a;-><init>(ILjava/lang/Object;)V` | yes |

## `Lapp/revanced/extension/vkvideo/ReVancedAdsGroupClick;` (4/4)

| reference | resolves |
|---|---|
| `La64/x;-><init>(Ljava/lang/Class;Ljava/lang/Class;Landroid/os/Bundle;)V` | yes |
| `La64/x;->t(Lcom/vk/core/fragments/FragmentImpl;)V` | yes |
| `Lcom/vk/core/fragments/FragmentImpl;` | yes |
| `Lcom/vk/video/screens/settings/main/ui/VideoUserSettingsFragment$a;` | yes |

## `Lapp/revanced/extension/vkvideo/ReVancedAdsSettingsFragment;` (17/17)

| reference | resolves |
|---|---|
| `Lcom/vk/core/fragments/FragmentImpl;-><init>()V` | yes |
| `Lcom/vk/core/fragments/FragmentImpl;->onViewCreated(Landroid/view/View;Landroid/os/Bundle;)V` | yes |
| `Lcom/vk/core/view/AppBarShadowView;` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar$Middle$Text$Title;` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar$Middle$Text$Title;-><init>(Ljava/lang/String;La52/c0;Lcom/vk/core/view/components/topbar/VkTopBar$Middle$Text$Title$a$a;Lcom/vk/core/view/components/topbar/VkTopBar$Middle$Text$Title$Size;I)V` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar$Middle$Text;` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar$Middle$Text;-><init>(Lcom/vk/core/view/components/topbar/VkTopBar$Middle$Text$Title;Lcom/vk/core/view/components/topbar/VkTopBar$Middle$Text$c;Lcom/vk/core/view/components/topbar/VkTopBar$Middle$Text$a$b;Lcom/vk/core/compose/component/semantics/SemanticsConfiguration;I)V` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar$c;` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar$c;-><init>(Lkotlin/jvm/functions/Function0;Ltx1/a;Lv72/e;Lcom/vk/core/compose/component/semantics/a;I)V` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar;` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar;-><init>(Landroid/content/Context;Landroid/util/AttributeSet;II)V` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar;->setBack(Lcom/vk/core/view/components/topbar/VkTopBar$c;)V` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar;->setBefore(Lcom/vk/core/view/components/topbar/VkTopBar$d;)V` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar;->setMiddle(Lcom/vk/core/view/components/topbar/VkTopBar$Middle;)V` | yes |
| `Ltx1/a;->Companion:Ltx1/a$a;` | yes |
| `Lv57/a;->f(Lcom/vk/core/fragments/FragmentImpl;Lcom/vk/core/view/components/topbar/VkTopBar;)Z` | yes |
| `Lxy1/j0;->m(Landroid/view/View;)V` | yes |

## `Lapp/revanced/extension/vkvideo/ReVancedBackClick;` (2/2)

| reference | resolves |
|---|---|
| `Lcom/vk/core/fragments/FragmentImpl;` | yes |
| `Lcom/vk/core/fragments/FragmentImpl;->getActivity()Landroidx/fragment/app/FragmentActivity;` | yes |

## `Lapp/revanced/extension/vkvideo/ReVancedRootFragment;` (17/17)

| reference | resolves |
|---|---|
| `Lcom/vk/core/fragments/FragmentImpl;-><init>()V` | yes |
| `Lcom/vk/core/fragments/FragmentImpl;->onViewCreated(Landroid/view/View;Landroid/os/Bundle;)V` | yes |
| `Lcom/vk/core/view/AppBarShadowView;` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar$Middle$Text$Title;` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar$Middle$Text$Title;-><init>(Ljava/lang/String;La52/c0;Lcom/vk/core/view/components/topbar/VkTopBar$Middle$Text$Title$a$a;Lcom/vk/core/view/components/topbar/VkTopBar$Middle$Text$Title$Size;I)V` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar$Middle$Text;` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar$Middle$Text;-><init>(Lcom/vk/core/view/components/topbar/VkTopBar$Middle$Text$Title;Lcom/vk/core/view/components/topbar/VkTopBar$Middle$Text$c;Lcom/vk/core/view/components/topbar/VkTopBar$Middle$Text$a$b;Lcom/vk/core/compose/component/semantics/SemanticsConfiguration;I)V` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar$c;` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar$c;-><init>(Lkotlin/jvm/functions/Function0;Ltx1/a;Lv72/e;Lcom/vk/core/compose/component/semantics/a;I)V` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar;` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar;-><init>(Landroid/content/Context;Landroid/util/AttributeSet;II)V` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar;->setBack(Lcom/vk/core/view/components/topbar/VkTopBar$c;)V` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar;->setBefore(Lcom/vk/core/view/components/topbar/VkTopBar$d;)V` | yes |
| `Lcom/vk/core/view/components/topbar/VkTopBar;->setMiddle(Lcom/vk/core/view/components/topbar/VkTopBar$Middle;)V` | yes |
| `Ltx1/a;->Companion:Ltx1/a$a;` | yes |
| `Lv57/a;->f(Lcom/vk/core/fragments/FragmentImpl;Lcom/vk/core/view/components/topbar/VkTopBar;)Z` | yes |
| `Lxy1/j0;->m(Landroid/view/View;)V` | yes |

## `Lapp/revanced/extension/vkvideo/StartReVancedSettings;` (3/3)

| reference | resolves |
|---|---|
| `La64/x;-><init>(Ljava/lang/Class;Ljava/lang/Class;Landroid/os/Bundle;)V` | yes |
| `La64/x;->s(Landroid/content/Context;)V` | yes |
| `Lcom/vk/video/screens/settings/main/ui/VideoUserSettingsFragment$a;` | yes |

## Gate self-test probes (not shipped)

## `Lapp/revanced/extension/probe/Broken;` (0/2)

| reference | resolves |
|---|---|
| `Lcom/vk/dto/common/Nope;` | **NO** |
| `Lcom/vk/dto/common/Nope;->zzz(IIIII)I` | **NO** |

## `Lapp/revanced/extension/probe/Probe;` (3/3)

| reference | resolves |
|---|---|
| `Lcom/vk/dto/common/VideoFile;` | yes |
| `Lcom/vk/dto/common/VideoFile;->O0()Lcom/vk/dto/common/id/UserId;` | yes |
| `Lcom/vk/dto/common/id/UserId;->c()J` | yes |

