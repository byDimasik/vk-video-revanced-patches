# In-Stream Video Ad Loader & InstreamAd Technical Reconnaissance Report

## Target APK Metadata
* **Package Name**: `com.vk.vkvideo`
* **Version Name**: `1.165`
* **Version Code**: `52786`
* **SHA-256 Checksum**: `d52c46e0af98a271a21d86d0694000e7718c210c194555c20982650c095cedac`

---

## Executive Summary & Architecture Overview

The VK Video in-stream video advertisement system relies on a multi-tiered pipeline involving:
1. **DTO Instantiation & JSON Deserialization** (`Lcom/vk/dto/common/InstreamAd;` and `Lcom/vk/dto/common/InstreamAd$b;`).
2. **Video Item Model Binding** (`Lcom/vk/dto/common/VideoFileOld;->x3()Lcom/vk/dto/common/InstreamAd;`).
3. **Low-Level Ad Engine Factory / Loader** (`Lmo/p6;->f(Lmo/d5;Lmo/c1;)V`).
4. **High-Level In-Stream Ad Controller Playback Trigger** (`Lwo/b;->c(Ljava/lang/String;)V`).

These methods are candidate interception points. The documented bodies show ad parsing, loading, and playback-entry operations.

---

## 1. Candidate Method 1: `InstreamAd` JSON Parser / Serializer (`InstreamAd$b.a`)

### Method Details
* **Class Descriptor**: `Lcom/vk/dto/common/InstreamAd$b;`
* **Method Name**: `a`
* **Method Signature**: `(Lorg/json/JSONObject;)Ljava/lang/Object;` (specialized as `(Lorg/json/JSONObject;)Lcom/vk/dto/common/InstreamAd;`)
* **Access Flags**: `public final`
* **Associated Source File**: `JsonParser.kt`

### String Literals
* `"slot_id"`
* `"timeout"`
* `"can_play"`
* `"midroll_percents"`
* `"params"`
* `"sections"`
* `"autoplay_preroll"`

### Un-Elided Smali Code

```smali
.method public final a(Lorg/json/JSONObject;)Ljava/lang/Object;
    .registers 15
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "(",
            "Lorg/json/JSONObject;",
            ")",
            "Lcom/vk/dto/common/InstreamAd;"
        }
    .end annotation

    .line 1
    :try_start_0
    const-string/jumbo v0, "slot_id"

    .line 2
    .line 3
    .line 4
    invoke-virtual {p1, v0}, Lorg/json/JSONObject;->optInt(Ljava/lang/String;)I

    .line 5
    .line 6
    .line 7
    move-result v6

    .line 8
    const-string/jumbo v0, "timeout"

    .line 9
    .line 10
    .line 11
    invoke-virtual {p1, v0}, Lorg/json/JSONObject;->optInt(Ljava/lang/String;)I

    .line 12
    .line 13
    .line 14
    move-result v7

    .line 15
    const-string v0, "can_play"

    .line 16
    .line 17
    invoke-virtual {p1, v0}, Lorg/json/JSONObject;->optInt(Ljava/lang/String;)I

    .line 18
    .line 19
    .line 20
    move-result v0

    .line 21
    const/4 v1, 0x0

    .line 22
    const/4 v2, 0x1

    .line 23
    if-ne v0, v2, :cond_1a

    .line 24
    .line 25
    move v0, v2

    .line 26
    goto :goto_1c

    .line 27
    :cond_1a
    move v0, v2

    .line 28
    move v2, v1

    .line 29
    :goto_1c
    const-string/jumbo v3, "midroll_percents"

    .line 30
    .line 31
    .line 32
    invoke-virtual {p1, v3}, Lorg/json/JSONObject;->optJSONArray(Ljava/lang/String;)Lorg/json/JSONArray;

    .line 33
    .line 34
    .line 35
    move-result-object v3

    .line 36
    if-eqz v3, :cond_49

    .line 37
    .line 38
    new-instance v4, Ljava/util/ArrayList;

    .line 39
    .line 40
    invoke-virtual {v3}, Lorg/json/JSONArray;->length()I

    .line 41
    .line 42
    .line 43
    move-result v5

    .line 44
    invoke-direct {v4, v5}, Ljava/util/ArrayList;-><init>(I)V

    .line 45
    .line 46
    .line 47
    invoke-virtual {v3}, Lorg/json/JSONArray;->length()I

    .line 48
    .line 49
    .line 50
    move-result v5

    .line 51
    move v8, v1

    .line 52
    :goto_33
    if-ge v8, v5, :cond_47

    .line 53
    .line 54
    invoke-virtual {v3, v8}, Lorg/json/JSONArray;->getString(I)Ljava/lang/String;

    .line 55
    .line 56
    .line 57
    move-result-object v9

    .line 58
    invoke-static {v9}, Ljava/lang/Float;->parseFloat(Ljava/lang/String;)F

    .line 59
    .line 60
    .line 61
    move-result v9

    .line 62
    invoke-static {v9}, Ljava/lang/Float;->valueOf(F)Ljava/lang/Float;

    .line 63
    .line 64
    .line 65
    move-result-object v9

    .line 66
    invoke-virtual {v4, v9}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    .line 67
    .line 68
    .line 69
    add-int/lit8 v8, v8, 0x1

    .line 70
    .line 71
    goto :goto_33

    .line 72
    :cond_47
    :goto_47
    move-object v3, v4

    .line 73
    goto :goto_4c

    .line 74
    :cond_49
    sget-object v4, Lkotlin/collections/EmptyList;->a:Lkotlin/collections/EmptyList;

    .line 75
    .line 76
    goto :goto_47

    .line 77
    :goto_4c
    const-string/jumbo v4, "params"

    .line 78
    .line 79
    .line 80
    invoke-virtual {p1, v4}, Lorg/json/JSONObject;->optJSONObject(Ljava/lang/String;)Lorg/json/JSONObject;

    .line 81
    .line 82
    .line 83
    move-result-object v4

    .line 84
    if-eqz v4, :cond_91

    .line 85
    .line 86
    invoke-static {v4}, Lcx1/s0;->o(Lorg/json/JSONObject;)Ljava/util/HashMap;

    .line 87
    .line 88
    .line 89
    move-result-object v4

    .line 90
    new-instance v5, Ljava/util/LinkedHashMap;

    .line 91
    .line 92
    invoke-virtual {v4}, Ljava/util/HashMap;->size()I

    .line 93
    .line 94
    .line 95
    move-result v8

    .line 96
    invoke-static {v8}, Lkotlin/collections/g0;->c(I)I

    .line 97
    .line 98
    .line 99
    move-result v8

    .line 100
    invoke-direct {v5, v8}, Ljava/util/LinkedHashMap;-><init>(I)V

    .line 101
    .line 102
    .line 103
    invoke-virtual {v4}, Ljava/util/HashMap;->entrySet()Ljava/util/Set;

    .line 104
    .line 105
    .line 106
    move-result-object v4

    .line 107
    check-cast v4, Ljava/lang/Iterable;

    .line 108
    .line 109
    invoke-interface {v4}, Ljava/lang/Iterable;->iterator()Ljava/util/Iterator;

    .line 110
    .line 111
    .line 112
    move-result-object v4

    .line 113
    :goto_70
    invoke-interface {v4}, Ljava/util/Iterator;->hasNext()Z

    .line 114
    .line 115
    .line 116
    move-result v8

    .line 117
    if-eqz v8, :cond_8f

    .line 118
    .line 119
    invoke-interface {v4}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    .line 120
    .line 121
    .line 122
    move-result-object v8

    .line 123
    move-object v9, v8

    .line 124
    check-cast v9, Ljava/util/Map$Entry;

    .line 125
    .line 126
    invoke-interface {v9}, Ljava/util/Map$Entry;->getKey()Ljava/lang/Object;

    .line 127
    .line 128
    .line 129
    move-result-object v9

    .line 130
    check-cast v8, Ljava/util/Map$Entry;

    .line 131
    .line 132
    invoke-interface {v8}, Ljava/util/Map$Entry;->getValue()Ljava/lang/Object;

    .line 133
    .line 134
    .line 135
    move-result-object v8

    .line 136
    invoke-virtual {v8}, Ljava/lang/Object;->toString()Ljava/lang/String;

    .line 137
    .line 138
    .line 139
    move-result-object v8

    .line 140
    invoke-interface {v5, v9, v8}, Ljava/util/Map;->put(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;

    .line 141
    .line 142
    .line 143
    goto :goto_70

    .line 144
    :cond_8f
    :goto_8f
    move-object v4, v5

    .line 145
    goto :goto_94

    .line 146
    :cond_91
    sget-object v5, Lkotlin/collections/y;->a:Lkotlin/collections/y;

    .line 147
    .line 148
    goto :goto_8f

    .line 149
    :goto_94
    const-string/jumbo v5, "sections"

    .line 150
    .line 151
    .line 152
    invoke-virtual {p1, v5}, Lorg/json/JSONObject;->optJSONArray(Ljava/lang/String;)Lorg/json/JSONArray;

    .line 153
    .line 154
    .line 155
    move-result-object v5

    .line 156
    if-eqz v5, :cond_c7

    .line 157
    .line 158
    new-instance v8, Ljava/util/ArrayList;

    .line 159
    .line 160
    invoke-virtual {v5}, Lorg/json/JSONArray;->length()I

    .line 161
    .line 162
    .line 163
    move-result v9

    .line 164
    invoke-direct {v8, v9}, Ljava/util/ArrayList;-><init>(I)V

    .line 165
    .line 166
    .line 167
    invoke-virtual {v5}, Lorg/json/JSONArray;->length()I

    .line 168
    .line 169
    .line 170
    move-result v9

    .line 171
    move v10, v1

    .line 172
    :goto_ab
    if-ge v10, v9, :cond_c1

    .line 173
    .line 174
    invoke-virtual {v5, v10}, Lorg/json/JSONArray;->getString(I)Ljava/lang/String;

    .line 175
    .line 176
    .line 177
    move-result-object v11

    .line 178
    sget-object v12, Ljava/util/Locale;->ROOT:Ljava/util/Locale;

    .line 179
    .line 180
    invoke-virtual {v11, v12}, Ljava/lang/String;->toUpperCase(Ljava/util/Locale;)Ljava/lang/String;

    .line 181
    .line 182
    .line 183
    move-result-object v11

    .line 184
    invoke-static {v11}, Lcom/vk/dto/common/AdSection;->valueOf(Ljava/lang/String;)Lcom/vk/dto/common/AdSection;

    .line 185
    .line 186
    .line 187
    move-result-object v11

    .line 188
    invoke-virtual {v8, v11}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    .line 189
    .line 190
    .line 191
    add-int/lit8 v10, v10, 0x1

    .line 192
    .line 193
    goto :goto_ab

    .line 194
    :cond_c1
    invoke-static {v8}, Lkotlin/collections/w;->Z0(Ljava/lang/Iterable;)Ljava/util/Set;

    .line 195
    .line 196
    .line 197
    move-result-object v5

    .line 198
    if-nez v5, :cond_c9

    .line 199
    .line 200
    :cond_c7
    sget-object v5, Lkotlin/collections/EmptySet;->a:Lkotlin/collections/EmptySet;

    .line 201
    .line 202
    :cond_c9
    const-string v8, "autoplay_preroll"

    .line 203
    .line 204
    invoke-virtual {p1, v8}, Lorg/json/JSONObject;->optInt(Ljava/lang/String;)I

    .line 205
    .line 206
    .line 207
    move-result p1

    .line 208
    if-ne p1, v0, :cond_d3

    .line 209
    .line 210
    move v8, v0

    .line 211
    goto :goto_d4

    .line 212
    :cond_d3
    move v8, v1

    .line 213
    :goto_d4
    new-instance v1, Lcom/vk/dto/common/InstreamAd;

    .line 214
    .line 215
    invoke-direct/range {v1 .. v8}, Lcom/vk/dto/common/InstreamAd;-><init>(ZLjava/util/List;Ljava/util/Map;Ljava/util/Set;IIZ)V
    :try_end_d9
    .catchall {:try_start_0 .. :try_end_d9} :catchall_da

    .line 216
    .line 217
    .line 218
    return-object v1

    .line 219
    :catchall_da
    const/4 p1, 0x0

    .line 220
    return-object p1
.end method
```

### ReVanced Opcode Sequence
`const-string/jumbo`, `invoke-virtual`, `move-result`, `const-string/jumbo`, `invoke-virtual`, `move-result`, `const-string`, `invoke-virtual`, `move-result`, `const/4`, `const/4`, `if-ne`, `move`, `goto`, `move`, `move`, `const-string/jumbo`, `invoke-virtual`, `move-result-object`, `if-eqz`, `new-instance`, `invoke-virtual`, `move-result`, `invoke-direct`, `invoke-virtual`, `move-result`, `move`, `if-ge`, `invoke-virtual`, `move-result-object`, `invoke-static`, `move-result`, `invoke-static`, `move-result-object`, `invoke-virtual`, `add-int/lit8`, `goto`, `move-object`, `goto`, `sget-object`, `goto`, `const-string/jumbo`, `invoke-virtual`, `move-result-object`, `if-eqz`, `invoke-static`, `move-result-object`, `new-instance`, `invoke-virtual`, `move-result`, `invoke-static`, `move-result`, `invoke-direct`, `invoke-virtual`, `move-result-object`, `check-cast`, `invoke-interface`, `move-result-object`, `invoke-interface`, `move-result`, `if-eqz`, `invoke-interface`, `move-result-object`, `move-object`, `check-cast`, `invoke-interface`, `move-result-object`, `check-cast`, `invoke-interface`, `move-result-object`, `invoke-virtual`, `move-result-object`, `invoke-interface`, `goto`, `move-object`, `goto`, `sget-object`, `goto`, `const-string/jumbo`, `invoke-virtual`, `move-result-object`, `if-eqz`, `new-instance`, `invoke-virtual`, `move-result`, `invoke-direct`, `invoke-virtual`, `move-result`, `move`, `if-ge`, `invoke-virtual`, `move-result-object`, `sget-object`, `invoke-virtual`, `move-result-object`, `invoke-static`, `move-result-object`, `invoke-virtual`, `add-int/lit8`, `goto`, `invoke-static`, `move-result-object`, `if-nez`, `sget-object`, `const-string`, `invoke-virtual`, `move-result`, `if-ne`, `move`, `goto`, `move`, `new-instance`, `invoke-direct/range`, `return-object`, `const/4`, `return-object`

### Caller Null-Safety & Early-Return Analysis
**Verified Caller Analysis**: Decompilation of `VideoFileOld.smali` demonstrates where `InstreamAd$b.a(JSONObject)` is invoked during JSON deserialization:

```smali
# VideoFileOld.smali lines 2477-2487
if-eqz v13, :cond_216
sget-object v15, Lcom/vk/dto/common/InstreamAd;->CREATOR:Lcom/vk/core/serialize/Serializer$c;
invoke-static {}, Lcom/vk/dto/common/InstreamAd$a;->a()Lcom/vk/dto/common/InstreamAd$b;
move-result-object v15
invoke-virtual {v15, v13}, Lcom/vk/dto/common/InstreamAd$b;->a(Lorg/json/JSONObject;)Ljava/lang/Object;
move-result-object v13
check-cast v13, Lcom/vk/dto/common/InstreamAd;
iput-object v13, v1, Lcom/vk/dto/common/VideoFileOld;->b0:Lcom/vk/dto/common/InstreamAd;
:cond_216
```

When `a(JSONObject)` returns `null` (or throws an exception caught by `:catchall_da`), `VideoFileOld.b0` is assigned `null`. Subsequent calls to `videoFile.x3()` then return `null`, which directly triggers the null branch in `VideoAutoPlay.smali` (documented in Candidate 2), setting the ad controller `v1 = null` and jumping to `:goto_4b3` to proceed directly with main video stream playback.

---

## 2. Candidate Method 2: Video Model In-Stream Ad Getter (`VideoFileOld.x3`)

### Method Details
* **Class Descriptor**: `Lcom/vk/dto/common/VideoFileOld;`
* **Method Name**: `x3`
* **Method Signature**: `()Lcom/vk/dto/common/InstreamAd;`
* **Access Flags**: `public final`
* **Associated Source File**: `VideoFileOld.java`

### String Literals
*(No string literals used in method body)*

### Un-Elided Smali Code

```smali
.method public final x3()Lcom/vk/dto/common/InstreamAd;
    .registers 2

    .line 1
    iget-object v0, p0, Lcom/vk/dto/common/VideoFileOld;->b0:Lcom/vk/dto/common/InstreamAd;

    .line 2
    .line 3
    return-object v0
.end method
```

### ReVanced Opcode Sequence
`iget-object`, `return-object`

### Caller Null-Safety & Early-Return Analysis

**Verified Caller Analysis**: Decompilation of the primary player controller `VideoAutoPlay.smali` demonstrates explicit null-safety branching without elisions. Note that lines 9773–10539 are omitted below as they form the internal ad setup block executed only when `v1 != null`:

```smali
# VideoAutoPlay.smali lines 9743-9772
    :cond_d7
    invoke-virtual {v2}, Lcom/vk/libvideo/autoplay/VideoAutoPlay;->d1()V

    .line 217
    .line 218
    .line 219
    iget-object v1, v2, Lcom/vk/libvideo/autoplay/VideoAutoPlay;->z:Lcom/vk/dto/common/VideoFile;

    .line 220
    .line 221
    invoke-interface {v1}, Lcom/vk/dto/common/VideoFile;->x3()Lcom/vk/dto/common/InstreamAd;

    .line 222
    .line 223
    .line 224
    move-result-object v1

    .line 225
    const/4 v8, 0x3

    .line 226
    const/4 v9, 0x2

    .line 227
    const/4 v10, 0x0

    .line 228
    if-eqz v1, :cond_25a

    .line 229
    .line 230
    invoke-virtual {v2}, Lcom/vk/libvideo/autoplay/VideoAutoPlay;->f1()Z

    .line 231
    .line 232
    .line 233
    move-result v11

    .line 234
    invoke-virtual {v2}, Lcom/vk/libvideo/autoplay/VideoAutoPlay;->e1()Lcom/vk/libvideo/autoplay/a;

    .line 235
    .line 236
    .line 237
    move-result-object v12

    .line 238
    iget-boolean v12, v12, Lcom/vk/libvideo/autoplay/a;->a:Z

    .line 239
    .line 240
    iget-object v13, v2, Lcom/vk/libvideo/autoplay/VideoAutoPlay;->B:Ljava/lang/String;

    .line 241
    .line 242
    if-eqz v13, :cond_f9
```

```smali
# VideoAutoPlay.smali lines 10540-10875
    move-object/from16 v1, v20

    .line 601
    .line 602
    goto :goto_25b

    .line 603
    :cond_25a
    move-object v1, v10

    .line 604
    :goto_25b
    invoke-virtual {v2}, Lcom/vk/libvideo/autoplay/VideoAutoPlay;->r1()Z

    .line 605
    .line 606
    .line 607
    move-result v4

    .line 608
    if-eqz v4, :cond_263

    .line 609
    .line 610
    :goto_261
    move-object v4, v10

    .line 611
    goto :goto_27b

    .line 612
    :cond_263
    if-nez v1, :cond_266

    .line 613
    .line 614
    goto :goto_261

    .line 615
    :cond_266
    iget-object v4, v2, Lcom/vk/libvideo/autoplay/VideoAutoPlay;->U:Li23/m;

    .line 616
    .line 617
    if-nez v4, :cond_27b

    .line 618
    .line 619
    new-instance v4, Li23/m;

    .line 620
    .line 621
    sget-object v5, Lz12/f;->a:Landroid/content/Context;

    .line 622
    .line 623
    if-eqz v5, :cond_271

    .line 624
    .line 625
    goto :goto_272

    .line 626
    :cond_271
    move-object v5, v10

    .line 627
    :goto_272
    iget-object v6, v2, Lcom/vk/libvideo/autoplay/VideoAutoPlay;->z:Lcom/vk/dto/common/VideoFile;

    .line 628
    .line 629
    invoke-interface {v6}, Lcom/vk/dto/common/VideoFile;->getDuration()I

    .line 630
    .line 631
    .line 632
    move-result v6

    .line 633
    invoke-direct {v4, v5, v1, v6}, Li23/m;-><init>(Landroid/content/Context;Lcom/vk/dto/common/InstreamAd;I)V

    .line 634
    .line 635
    .line 636
    :cond_27b
    :goto_27b
    iput-object v4, v2, Lcom/vk/libvideo/autoplay/VideoAutoPlay;->U:Li23/m;

    .line 637
    .line 638
    if-eqz v4, :cond_2e4

    .line 639
    .line 640
    iget-object v5, v4, Li23/m;->b:Lcom/vk/dto/common/InstreamAd;

    .line 641
    .line 642
    iget-boolean v6, v4, Li23/m;->e:Z

    .line 643
    .line 644
    if-nez v6, :cond_2e4

    .line 645
    .line 646
    iget v6, v5, Lcom/vk/dto/common/InstreamAd;->e:I

    .line 647
    .line 648
    iget v11, v4, Li23/m;->c:I

    .line 649
    .line 650
    iget-object v12, v4, Li23/m;->a:Landroid/content/Context;

    .line 651
    .line 652
    new-instance v13, Lmo/a;

    .line 653
    .line 654
    invoke-direct {v13, v6, v11, v12}, Lmo/a;-><init>(IILandroid/content/Context;)V

    .line 655
    .line 656
    .line 657
    iput-object v4, v13, Lmo/a;->i:Li23/m;

    .line 658
    .line 659
    iget-object v5, v5, Lcom/vk/dto/common/InstreamAd;->c:Ljava/util/Map;

    .line 660
    .line 661
    invoke-interface {v5}, Ljava/util/Map;->entrySet()Ljava/util/Set;

    .line 662
    .line 663
    .line 664
    move-result-object v5

    .line 665
    invoke-interface {v5}, Ljava/util/Set;->iterator()Ljava/util/Iterator;

    .line 666
    .line 667
    .line 668
    move-result-object v5

    .line 669
    :cond_29c
    :goto_29c
    invoke-interface {v5}, Ljava/util/Iterator;->hasNext()Z

    .line 670
    .line 671
    .line 672
    move-result v6

    # ... lines 10673-10838 omitted for conciseness ...

    .line 739
    iput-boolean v7, v4, Li23/m;->e:Z

    .line 740
    .line 741
    :cond_2e4
    invoke-virtual {v2}, Lcom/vk/libvideo/autoplay/VideoAutoPlay;->r1()Z

    .line 742
    .line 743
    .line 744
    move-result v4

    .line 745
    if-eqz v4, :cond_2ee

    .line 746
    .line 747
    iput-object v10, v2, Lcom/vk/libvideo/autoplay/VideoAutoPlay;->T:Li23/h;

    .line 748
    .line 749
    goto/16 :goto_4b3

    .line 750
    .line 751
    :cond_2ee
    if-nez v1, :cond_2f2

    .line 752
    .line 753
    goto/16 :goto_4b3

    .line 754
    .line 755
    :cond_2f2
    iget-object v4, v2, Lcom/vk/libvideo/autoplay/VideoAutoPlay;->T:Li23/h;
```

```smali
# VideoAutoPlay.smali lines 11773-11785
    :goto_4b3
    invoke-virtual {v2}, Lcom/vk/libvideo/autoplay/VideoAutoPlay;->e1()Lcom/vk/libvideo/autoplay/a;

    .line 1205
    .line 1206
    .line 1207
    move-result-object v1

    .line 1208
    iget-boolean v1, v1, Lcom/vk/libvideo/autoplay/a;->f:Z

    .line 1209
    .line 1210
    if-eqz v1, :cond_4ff

    .line 1211
    .line 1212
    iget-object v1, v2, Lcom/vk/libvideo/autoplay/VideoAutoPlay;->r0:Lcom/vk/media/player/video/e;
```

When `x3()` returns `null` (e.g. by replacing `iget-object` with `const/4 v0, 0x0`), `VideoAutoPlay` branch `if-eqz v1, :cond_25a` jumps directly to `:cond_25a`, setting `v1 = v10` (null ad controller). Then, `if-nez v1, :cond_266` is false because `v1` is null, so execution falls through to `goto :goto_261`, setting `v4 = v10` (null ad view) and bypassing `Li23/m;` ad controller creation. Later, at line 10851, `if-nez v1, :cond_2f2` evaluates to true, taking `goto/16 :goto_4b3`, which lands at `:goto_4b3` (line 11773) to proceed directly with main video stream player initialization.

---

## 3. Candidate Method 3: Ad Engine Entry Point / Loader (`p6.f`)

### Method Details
* **Class Descriptor**: `Lmo/p6;`
* **Method Name**: `f`
* **Method Signature**: `(Lmo/d5;Lmo/c1;)V`
* **Access Flags**: `public final`
* **Associated Source File**: Decompiled from DEX (`mo/p6.smali`)

### String Literals
* `"version"`
* `"2.5"`
* `"s2s"`
* `"InstreamAdFactory: invalid json-data, error: "`

### Un-Elided Smali Code

```smali
.method public final f(Lmo/d5;Lmo/c1;)V
    .registers 16

    .line 1
    iget-object v0, p0, Lmo/p6;->h:Ljava/lang/String;

    .line 2
    .line 3
    if-eqz v0, :cond_7c

    .line 4
    .line 5
    :try_start_4
    iget-object v1, p0, Lmo/l6;->b:Lmo/o5;

    .line 6
    .line 7
    iget-object v1, v1, Lmo/o5;->h:Ljava/lang/String;

    .line 8
    .line 9
    new-instance v2, Lorg/json/JSONObject;

    .line 10
    .line 11
    invoke-direct {v2, v0}, Lorg/json/JSONObject;-><init>(Ljava/lang/String;)V

    .line 12
    .line 13
    .line 14
    const-string/jumbo v3, "version"

    .line 15
    .line 16
    .line 17
    invoke-virtual {v2, v3}, Lorg/json/JSONObject;->has(Ljava/lang/String;)Z

    .line 18
    .line 19
    .line 20
    move-result v4
    :try_end_14
    .catchall {:try_start_4 .. :try_end_14} :catchall_58

    .line 21
    if-eqz v4, :cond_23

    .line 22
    .line 23
    :try_start_16
    invoke-virtual {v2, v3}, Lorg/json/JSONObject;->isNull(Ljava/lang/String;)Z

    .line 24
    .line 25
    .line 26
    move-result v4
    :try_end_1a
    .catchall {:try_start_16 .. :try_end_1a} :catchall_1e

    .line 27
    if-nez v4, :cond_23

    .line 28
    .line 29
    :goto_1c
    move-object v4, v0

    .line 30
    goto :goto_37

    .line 31
    :catchall_1e
    move-exception v0

    .line 32
    move-object p2, v0

    .line 33
    move-object v1, p0

    .line 34
    move-object v5, p1

    .line 35
    goto :goto_5c

    .line 36
    :cond_23
    :try_start_23
    new-instance v0, Lorg/json/JSONObject;

    .line 37
    .line 38
    invoke-direct {v0}, Lorg/json/JSONObject;-><init>()V

    .line 39
    .line 40
    .line 41
    const-string v4, "2.5"

    .line 42
    .line 43
    invoke-virtual {v0, v3, v4}, Lorg/json/JSONObject;->put(Ljava/lang/String;Ljava/lang/Object;)Lorg/json/JSONObject;

    .line 44
    .line 45
    .line 46
    move-result-object v0

    .line 47
    invoke-virtual {v0, v1, v2}, Lorg/json/JSONObject;->put(Ljava/lang/String;Ljava/lang/Object;)Lorg/json/JSONObject;

    .line 48
    .line 49
    .line 50
    move-result-object v0

    .line 51
    invoke-virtual {v0}, Lorg/json/JSONObject;->toString()Ljava/lang/String;

    .line 52
    .line 53
    .line 54
    move-result-object v0
    :try_end_36
    .catchall {:try_start_23 .. :try_end_36} :catchall_53

    .line 55
    goto :goto_1c

    .line 56
    :goto_37
    iget-object v0, p0, Lmo/l6;->a:Lmo/l6$a;

    .line 57
    .line 58
    invoke-interface {v0}, Lmo/l6$a;->b()Lmo/d;

    .line 59
    .line 60
    .line 61
    move-result-object v0

    .line 62
    iget-object v1, p0, Lmo/l6;->b:Lmo/o5;

    .line 63
    .line 64
    new-instance v2, Ljava/util/HashMap;

    .line 65
    .line 66
    invoke-direct {v2}, Ljava/util/HashMap;-><init>()V

    .line 67
    .line 68
    .line 69
    invoke-virtual {v0, v1, v2, p1, p2}, Lmo/d;->a(Lmo/o5;Ljava/util/Map;Lmo/d5;Lmo/c1;)Ljava/lang/String;

    .line 70
    .line 71
    .line 72
    move-result-object v3

    .line 73
    const-string v2, "s2s"

    .line 74
    .line 75
    const/4 v6, 0x0

    .line 76
    move-object v1, p0

    .line 77
    move-object v5, p1

    .line 78
    invoke-virtual/range {v1 .. v6}, Lmo/l6;->c(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lmo/d5;Lmo/m7;)V

    .line 79
    .line 80
    .line 81
    return-void

    .line 82
    :goto_51
    move-object p2, p1

    .line 83
    goto :goto_5c

    .line 84
    :catchall_53
    move-exception v0

    .line 85
    move-object v1, p0

    .line 86
    move-object v5, p1

    .line 87
    move-object p1, v0

    .line 88
    goto :goto_51

    .line 89
    :catchall_58
    move-exception v0

    .line 90
    move-object v1, p0

    .line 91
    move-object v5, p1

    .line 92
    move-object p2, v0

    .line 93
    :goto_5c
    new-instance p1, Ljava/lang/StringBuilder;

    .line 94
    .line 95
    const-string v0, "InstreamAdFactory: invalid json-data, error: "

    .line 96
    .line 97
    invoke-direct {p1, v0}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    .line 98
    .line 99
    .line 100
    invoke-virtual {p2}, Ljava/lang/Throwable;->getMessage()Ljava/lang/String;

    .line 101
    .line 102
    .line 103
    move-result-object p2

    .line 104
    invoke-virtual {p1, p2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    .line 105
    .line 106
    .line 107
    invoke-virtual {p1}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    .line 108
    .line 109
    .line 110
    move-result-object p1

    .line 111
    invoke-static {p1}, Lpg/c;->d(Ljava/lang/String;)V

    .line 112
    .line 113
    .line 114
    sget-object p1, Lmo/h7;->k:Lmo/h7;

    .line 115
    .line 116
    invoke-static {p1}, Lcom/google/gson/internal/h;->b(Lmo/h7;)Lcom/google/gson/internal/h;

    .line 117
    .line 118
    .line 119
    move-result-object p1

    .line 120
    const/4 v0, 0x0

    .line 121
    invoke-virtual {p0, v0, p1, v5}, Lmo/l6;->g(Lmo/xb;Lcom/google/gson/internal/h;Lmo/d5;)V

    .line 122
    .line 123
    .line 124
    return-void

    .line 125
    :cond_7c
    move-object v1, p0

    .line 126
    move-object v5, p1

    .line 127
    iget-object v8, v1, Lmo/p6;->e:Ljava/util/List;

    .line 128
    .line 129
    if-eqz v8, :cond_a7

    .line 130
    .line 131
    new-instance v0, Lcom/google/gson/internal/h;

    .line 132
    .line 133
    invoke-direct {v0}, Ljava/lang/Object;-><init>()V

    .line 134
    .line 135
    .line 136
    iget-object p1, v1, Lmo/l6;->a:Lmo/l6$a;

    .line 137
    .line 138
    invoke-interface {p1}, Lmo/l6$a;->d()Lmo/xa;

    .line 139
    .line 140
    .line 141
    move-result-object v10

    .line 142
    const/4 v9, 0x0

    .line 143
    move-object v7, v1

    .line 144
    move-object v11, v5

    .line 145
    move-object v12, v0

    .line 146
    invoke-virtual/range {v7 .. v12}, Lmo/l6;->b(Ljava/util/List;Lmo/xb;Lmo/xa;Lmo/d5;Lcom/google/gson/internal/h;)Lmo/xb;

    .line 147
    .line 148
    .line 149
    move-result-object p1

    .line 150
    check-cast p1, Lmo/l9;

    .line 151
    .line 152
    invoke-virtual {p0, p1, v0}, Lmo/l6;->h(Lmo/xb;Lcom/google/gson/internal/h;)Lmo/xb;

    .line 153
    .line 154
    .line 155
    move-result-object p1

    .line 156
    check-cast p1, Lmo/l9;

    .line 157
    .line 158
    if-eqz p1, :cond_a3

    .line 159
    .line 160
    new-instance v0, Lcom/google/gson/internal/h;

    .line 161
    .line 162
    invoke-direct {v0}, Ljava/lang/Object;-><init>()V

    .line 163
    .line 164
    :cond_a3
    invoke-virtual {p0, p1, v0, v5}, Lmo/l6;->g(Lmo/xb;Lcom/google/gson/internal/h;Lmo/d5;)V

    .line 165
    .line 166
    .line 167
    return-void

    .line 168
    :cond_a7
    invoke-super {p0, v5, p2}, Lmo/l6;->f(Lmo/d5;Lmo/c1;)V

    .line 169
    .line 170
    .line 171
    return-void
.end method
```

### ReVanced Opcode Sequence
`iget-object`, `if-eqz`, `iget-object`, `iget-object`, `new-instance`, `invoke-direct`, `const-string/jumbo`, `invoke-virtual`, `move-result`, `if-eqz`, `invoke-virtual`, `move-result`, `if-nez`, `move-object`, `goto`, `move-exception`, `move-object`, `move-object`, `move-object`, `goto`, `new-instance`, `invoke-direct`, `const-string`, `invoke-virtual`, `move-result-object`, `invoke-virtual`, `move-result-object`, `invoke-virtual`, `move-result-object`, `goto`, `iget-object`, `invoke-interface`, `move-result-object`, `iget-object`, `new-instance`, `invoke-direct`, `invoke-virtual`, `move-result-object`, `const-string`, `const/4`, `move-object`, `move-object`, `invoke-virtual/range`, `return-void`, `move-object`, `goto`, `move-exception`, `move-object`, `move-object`, `move-object`, `goto`, `move-exception`, `move-object`, `move-object`, `move-object`, `new-instance`, `const-string`, `invoke-direct`, `invoke-virtual`, `move-result-object`, `invoke-virtual`, `invoke-virtual`, `move-result-object`, `invoke-static`, `sget-object`, `invoke-static`, `move-result-object`, `const/4`, `invoke-virtual`, `return-void`, `move-object`, `move-object`, `iget-object`, `if-eqz`, `new-instance`, `invoke-direct`, `iget-object`, `invoke-interface`, `move-result-object`, `const/4`, `move-object`, `move-object`, `move-object`, `invoke-virtual/range`, `move-result-object`, `check-cast`, `invoke-virtual`, `move-result-object`, `check-cast`, `if-eqz`, `new-instance`, `invoke-direct`, `invoke-virtual`, `return-void`, `invoke-super`, `return-void`

### Caller Null-Safety & Early-Return Analysis
Inserting `return-void` at line 1 of `p6.f` short-circuits ad fetching/parsing for myTarget instream ads. As `f(d5, c1)` returns `void`, an early `return-void` stops network and s2s ad requests.

---

## 4. Candidate Method 4: High-Level In-Stream Ad Controller Playback Trigger (`wo/b.c`)

### Method Details
* **Class Descriptor**: `Lwo/b;`
* **Method Name**: `c`
* **Method Signature**: `(Ljava/lang/String;)V`
* **Access Flags**: `public final`
* **Associated Source File**: Decompiled from DEX (`wo/b.smali`)

### String Literals
* `"InstreamAd: Unable to start ad - not loaded yet"`
* `"InstreamAd: Unable to start ad - player has not set"`
* `"InstreamAdEngine: No section with name "`

### Un-Elided Smali Code

```smali
.method public final c(Ljava/lang/String;)V
    .registers 4

    .line 1
    iget-object v0, p0, Lwo/b;->i:Lmo/y5;

    .line 2
    .line 3
    if-nez v0, :cond_a

    .line 4
    .line 5
    const-string p1, "InstreamAd: Unable to start ad - not loaded yet"

    .line 6
    .line 7
    invoke-static {p1}, Lpg/c;->d(Ljava/lang/String;)V

    .line 8
    .line 9
    .line 10
    return-void

    .line 11
    :cond_a
    iget-object v1, v0, Lmo/y5;->d:Lmo/ta;

    .line 12
    .line 13
    iget-object v1, v1, Lmo/ta;->g:Lwo/c;

    .line 14
    .line 15
    if-nez v1, :cond_16

    .line 16
    .line 17
    const-string p1, "InstreamAd: Unable to start ad - player has not set"

    .line 18
    .line 19
    invoke-static {p1}, Lpg/c;->d(Ljava/lang/String;)V

    .line 20
    .line 21
    .line 22
    return-void

    .line 23
    :cond_16
    iget-object v1, v0, Lmo/y5;->j:Lmo/b0;

    .line 24
    .line 25
    invoke-virtual {v0, v1}, Lmo/y5;->d(Lmo/b0;)V

    .line 26
    .line 27
    .line 28
    iget-object v1, v0, Lmo/y5;->b:Lmo/l9;

    .line 29
    .line 30
    invoke-virtual {v1, p1}, Lmo/l9;->b(Ljava/lang/String;)Lmo/k;

    .line 31
    .line 32
    .line 33
    move-result-object v1

    .line 34
    if-eqz v1, :cond_29

    .line 35
    .line 36
    const/high16 p1, -0x40800000    # -1.0f

    .line 37
    .line 38
    invoke-virtual {v0, v1, p1}, Lmo/y5;->b(Lmo/k;F)V

    .line 39
    .line 40
    .line 41
    return-void

    .line 42
    :cond_29
    const-string v0, "InstreamAdEngine: No section with name "

    .line 43
    .line 44
    invoke-virtual {v0, p1}, Ljava/lang/String;->concat(Ljava/lang/String;)Ljava/lang/String;

    .line 45
    .line 46
    .line 47
    move-result-object p1

    .line 48
    invoke-static {p1}, Lpg/c;->d(Ljava/lang/String;)V

    .line 49
    .line 50
    .line 51
    return-void
.end method
```

### ReVanced Opcode Sequence
`iget-object`, `if-nez`, `const-string`, `invoke-static`, `return-void`, `iget-object`, `iget-object`, `if-nez`, `const-string`, `invoke-static`, `return-void`, `iget-object`, `invoke-virtual`, `iget-object`, `invoke-virtual`, `move-result-object`, `if-eqz`, `const/high16`, `invoke-virtual`, `return-void`, `const-string`, `invoke-virtual`, `move-result-object`, `invoke-static`, `return-void`

### Caller Null-Safety & Early-Return Analysis
Method `wo/b.c(String section)` triggers playback for named ad sections (`"preroll"`, `"midroll"`). Short-circuiting `c(String)` with an early `return-void` at line 1 prevents section playback initiation without throwing exceptions.
