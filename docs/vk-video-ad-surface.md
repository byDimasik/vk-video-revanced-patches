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
   * **`Lcom/vk/api/generated/video/dto/VideoVideoAdsInstreamDto;`**
   * **`Lcom/vk/api/generated/video/dto/VideoVideoAdsMobileInstreamadsDto;`**
   * **`Lcom/vk/dto/common/InstreamAd;`**
   * **`Lcom/iab/omid/library/corpmailru/adsession/media/VastProperties;`**
   * **`Lcom/vk/api/generated/apps/dto/AppsAdsSlotsVastDto;`**

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

    # Updates ad progress text and progress bar
    invoke-virtual {v2, p1}, Landroid/widget/TextView;->setText(Ljava/lang/CharSequence;)V

    return-void
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
  * `catalog_video_topshelf` (`0x7f0e026d`)
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
