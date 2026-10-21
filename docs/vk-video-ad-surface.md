# VK Video Ad-Surface Reconnaissance Report

## Target APK Metadata
* **Package Name**: `com.vk.vkvideo`
* **Version Name**: `1.165`
* **Version Code**: `52786`
* **SHA-256 Checksum**: `d52c46e0af98a271a21d86d0694000e7718c210c194555c20982650c095cedac`
* **Distribution Source**: APKPure Mobile API (`api.pureapk.com`)

---

## 1. Video Player Ad Controllers & VAST Loaders

VK Video uses an integrated ad execution framework combining the VK Ad Network (`com.my.target`), `one.video.ad`, and `com.vk.libvideo.design.view.ad` controllers.

### Key Classes & Descriptors

1. **`Lcom/vk/libvideo/design/view/ad/VideoAdLayout;`**
   * **Descriptor**: `Lcom/vk/libvideo/design/view/ad/VideoAdLayout;`
   * **Description**: Primary UI container hosting in-stream ad overlays and player controls during ad playback.
   * **Key Methods**:
     * `private final setOneVideoAdControlsView(Lone/video/ad/ux/controls/OneVideoAdBaseControls;)V`
     * `public final a(Le37/b;Lcom/vk/libvideo/design/view/ad/data/a;Lf73/a;Le73/b;Le73/d;Lkotlin/Lazy;)V`
     * `public final c(Li73/a;Le73/d;)V`
     * `public final d(Lme7/c;Ljava/lang/Boolean;)V`

2. **`Lone/video/ad/ux/controls/OneVideoAdControlsView;`**
   * **Descriptor**: `Lone/video/ad/ux/controls/OneVideoAdControlsView;`
   * **Description**: Video ad controls view handling skip button, ad progress bar, ad title, and shoppable product links.
   * **Key Methods**:
     * `public setAdBannerData(Lme7/a;)V`
     * `public setAdDisableData(Lme7/b;)V`
     * `public setAdProgress(Lme7/c;)V`
     * `public setPlayPauseControl(Z)V`

3. **In-Stream Ad DTOs & VAST Loaders**:
   * **`Lcom/vk/dto/common/InstreamAd;`**
     * **Descriptor**: `Lcom/vk/dto/common/InstreamAd;`
     * **Methods**: `public final l3(Lcom/vk/core/serialize/Serializer;)V`
   * **`Lcom/vk/api/generated/apps/dto/AppsAdsSlotsVastDto;`**
     * **Descriptor**: `Lcom/vk/api/generated/apps/dto/AppsAdsSlotsVastDto;`
     * **Methods**: `public final writeToParcel(Landroid/os/Parcel;I)V`
   * **`Lcom/iab/omid/library/corpmailru/adsession/media/VastProperties;`**
     * **Descriptor**: `Lcom/iab/omid/library/corpmailru/adsession/media/VastProperties;`
     * **Methods**: `public static createVastPropertiesForSkippableMedia(FZLcom/iab/omid/library/corpmailru/adsession/media/Position;)Lcom/iab/omid/library/corpmailru/adsession/media/VastProperties;`

### Relevant Smali Snippet (`OneVideoAdControlsView.setAdProgress`)

```smali
.method public setAdProgress(Lme7/c;)V
    .locals 6

    .line 1
    iget v0, p1, Lme7/c;->a:I

    .line 3
    iget-object v1, p0, Lone/video/ad/ux/controls/OneVideoAdControlsView;->s:Lve7/a;

    .line 5
    iget-object v2, v1, Lve7/a;->e:Landroidx/appcompat/widget/AppCompatTextView;

    .line 7
    const/4 v3, 0x0

    .line 8
    invoke-virtual {v2, v3}, Landroid/view/View;->setVisibility(I)V

    .line 11
    new-instance v3, Ley0/d;

    .line 13
    const/4 v4, 0x3

    .line 14
    invoke-direct {v3, v4, p1, p0}, Ley0/d;-><init>(ILjava/lang/Object;Ljava/lang/Object;)V

    .line 17
    invoke-virtual {v2, v3}, Landroid/view/View;->setOnClickListener(Landroid/view/View$OnClickListener;)V

    .line 20
    iget-object p1, p1, Lme7/c;->b:Ljava/lang/Integer;

    .line 22
    if-nez p1, :cond_0

    .line 24
    invoke-virtual {v2}, Landroid/view/View;->getContext()Landroid/content/Context;

    .line 27
    move-result-object p1

    .line 28
    const v3, 0x7f141c63

    .line 31
    invoke-virtual {p1, v3}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    .line 34
    move-result-object p1

    .line 35
    goto :goto_0

    .line 36
    :cond_0
    invoke-virtual {p1}, Ljava/lang/Integer;->intValue()I

    .line 39
    move-result p1

    .line 40
    int-to-float p1, p1

    .line 41
    const/high16 v3, 0x447a0000    # 1000.0f

    .line 43
    div-float/2addr p1, v3

    .line 44
    invoke-static {p1}, Ld17/a;->b(F)I

    .line 47
    move-result p1

    .line 48
    if-lez p1, :cond_1

    .line 49
    invoke-static {p1}, Ljava/lang/String;->valueOf(I)Ljava/lang/String;

    .line 53
    move-result-object p1

    .line 54
    goto :goto_0

    .line 55
    :cond_1
    invoke-virtual {v2}, Landroid/view/View;->getContext()Landroid/content/Context;

    .line 58
    move-result-object p1

    .line 59
    const v3, 0x7f141c61

    .line 62
    invoke-virtual {p1, v3}, Landroid/content/Context;->getString(I)Ljava/lang/String;

    .line 65
    move-result-object p1

    .line 66
    :goto_0
    invoke-virtual {v2, p1}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    .line 69
    iget-object p1, v1, Lve7/a;->b:Landroid/widget/ProgressBar;

    .line 71
    invoke-virtual {p1}, Landroid/widget/ProgressBar;->getProgress()I

    .line 74
    move-result v2

    .line 75
    if-eqz v2, :cond_2

    .line 77
    invoke-virtual {p1}, Landroid/widget/ProgressBar;->getProgress()I

    .line 79
    move-result v2

    .line 81
    sub-int/2addr v2, v0

    .line 82
    invoke-static {v2}, Ljava/lang/Math;->abs(I)I

    .line 85
    move-result v2

    .line 86
    int-to-long v2, v2

    .line 87
    const-wide/16 v4, 0x64

    .line 89
    cmp-long v2, v2, v4

    .line 91
    if-lez v2, :cond_3

    .line 93
    :cond_2
    invoke-virtual {p1, v0}, Landroid/widget/ProgressBar;->setProgress(I)V

    .line 96
    :cond_3
    iget-object p1, v1, Lve7/a;->g:Lone/video/ad/ux/motion/view/OneVideoMotionView;

    .line 98
    const/16 v0, 0x8

    .line 99
    invoke-virtual {p1, v0}, Landroid/view/View;->setVisibility(I)V

    .line 103
    return-void
.end method
```

### Relevant VAST Property Creator Snippet (`VastProperties.createVastPropertiesForSkippableMedia`)

```smali
.method public static createVastPropertiesForSkippableMedia(FZLcom/iab/omid/library/corpmailru/adsession/media/Position;)Lcom/iab/omid/library/corpmailru/adsession/media/VastProperties;
    .locals 3

    const-string v0, "Position is null"

    invoke-static {p2, v0}, Lcom/iab/omid/library/corpmailru/d/e;->a(Ljava/lang/Object;Ljava/lang/String;)V

    new-instance v0, Lcom/iab/omid/library/corpmailru/adsession/media/VastProperties;

    const/4 v1, 01

    invoke-static {p0}, Ljava/lang/Float;->valueOf(F)Ljava/lang/Float;

    move-result-object p0

    invoke-direct {0, v1, p0, p1, p2}, Lcom/iab/omid/library/corpmailru/adsession/media/VastProperties;-><init>(ZLjava/lang/Float;ZLcom/iab/omid/library/corpmailru/adsession/media/Position;)V

    return-object v0
.end method
```

---

## 2. Feed & Banner Ad Models and Parsers

Feed items, native banners, and promoted posts are injected into newsfeeds and catalogs using dedicated DTO models and JSON deserializer methods.

### Key Models & Parser Descriptors

1. **`Lcom/vk/dto/newsfeed/entries/MyTargetNativeAdEntry;`**
   * **Descriptor**: `Lcom/vk/dto/newsfeed/entries/MyTargetNativeAdEntry;`
   * **JSON Parser**: `Lcom/vk/dto/newsfeed/entries/MyTargetNativeAdEntry$a;->a(Lorg/json/JSONObject;Lap/d;)Lcom/vk/dto/newsfeed/entries/MyTargetNativeAdEntry;`
   * **Serializer**: `Lcom/vk/dto/newsfeed/entries/MyTargetNativeAdEntry$c;`

2. **`Lcom/vk/dto/newsfeed/entries/PromoPost;`**
   * **Descriptor**: `Lcom/vk/dto/newsfeed/entries/PromoPost;`
   * **JSON Parser**: `Lcom/vk/dto/newsfeed/entries/PromoPost$a;->a(Lorg/json/JSONObject;Landroid/util/ArrayMap;Landroid/util/SparseArray;Ljava/util/Map;)Lcom/vk/dto/newsfeed/entries/PromoPost;`
   * **Serializer**: `Lcom/vk/dto/newsfeed/entries/PromoPost$b;`

3. **`Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;`**
   * **Descriptor**: `Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;`
   * **Field**: `private final slotId:Ljava/lang/Long;`

4. **`Lcom/vk/dto/ads/AdBannerStyle;`**
   * **Descriptor**: `Lcom/vk/dto/ads/AdBannerStyle;`

### Relevant Smali Snippet (`MyTargetNativeAdEntry$a.a`)

```smali
.method public static a(Lorg/json/JSONObject;Lap/d;)Lcom/vk/dto/newsfeed/entries/MyTargetNativeAdEntry;
    .locals 23

    # Parses MyTarget native ad entry JSON payload into MyTargetNativeAdEntry
    .annotation sys:Lorg/json/JSONObject;
    .end annotation

    # ... JSON field extraction logic ...
    return-object v0
.end method
```

---

## 3. Sponsored Shelf UI Components & Resource IDs

Promotional cards, sponsored shelves, catalog ad banners, and video player ad overlays utilize specific layout files and resource identifiers.

### Top Shelf / Sponsored Shelf Layouts & Resource IDs
* **Layouts**:
  * `catalog_video_music_topshelf` (`0x7f0e026d`)
  * `catalog_video_topshelf` (`0x7f0e028b`)
* **View IDs**:
  * `catalog_video_music_topshelf` (`0x7f0b05fe`)
  * `video_topshelf_root` (`0x7f0b29b7`)
  * `video_topshelf_view_pager` (`0x7f0b29b8`)
  * `video_topshelf_indicators` (`0x7f0b29b6`)
  * `topshelf_button_primary` (`0x7f0b26c3`)
  * `topshelf_button_secondary` (`0x7f0b26c4`)

### Feed & Catalog Ad Banner Layouts & Resource IDs
* **Layouts**:
  * `catalog_ad_banner` (`0x7f0e011e`)
  * `catalog_ad_banner_medium` (`0x7f0e011f`)
  * `vk_ad_banner` (`0x7f0e0d52`)
  * `video_ad_banner` (`0x7f0e0c6f`)
* **View IDs**:
  * `ad_banner_image` (`0x7f0b00c8`)
  * `open_ad_banner` (`0x7f0b1a8c`)
  * `vk_ad_banner_content` (`0x7f0b2a34`)
  * `vk_ad_banner_title` (`0x7f0b2a37`)
  * `vk_ad_banner_button` (`0x7f0b2a33`)
  * `vk_ad_banner_badge_container` (`0x7f0b2a30`)

### Video Player Ad Overlay Layouts & Resource IDs
* **Layouts**:
  * `one_video_ad_controls_view` (`0x7f0e0970`)
  * `one_video_ad_controls_view_new` (`0x7f0e0971`)
  * `video_ad_view` (`0x7f0e0c71`)
  * `overlay_ad_banner_player` (`0x7f0e099c`)
* **View IDs**:
  * `video_ad_layout` (`0x7f0b289e`)
  * `one_video_ad_controls_view` (`0x7f0b1a52`)
  * `ad_controls_view` (`0x7f0b00d0`)
  * `video_ad_skip` (`0x7f0b28a4`)
  * `video_ad_disable` (`0x7f0b289d`)
  * `video_ad_progress_bar` (`0x7f0b28a1`)
  * `video_ad_redirect` (`0x7f0b28a2`)
  * `video_ad_title` (`0x7f0b28a5`)
