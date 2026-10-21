# VK Video Feed and Catalog Ad Layer Reconnaissance

## Target APK Metadata
* **Package Name**: `com.vk.vkvideo`
* **Version Name**: `1.165`
* **Version Code**: `52786`
* **SHA-256 Checksum**: `d52c46e0af98a271a21d86d0694000e7718c210c194555c20982650c095cedac`

---

## 1. Overview

This document provides complete, un-elided Smali method bodies and architectural analysis for the feed ad entry parsers and catalog ad banner DTOs in VK Video (`com.vk.vkvideo` v1.165).

The ad surface covered in this report includes:
1. **MyTarget Native Ad Entry Deserializer**: `Lcom/vk/dto/newsfeed/entries/MyTargetNativeAdEntry$a;->a(Lorg/json/JSONObject;Lap/d;)Lcom/vk/dto/newsfeed/entries/MyTargetNativeAdEntry;`
2. **PromoPost Deserializer**: `Lcom/vk/dto/newsfeed/entries/PromoPost$a;->a(Lorg/json/JSONObject;Landroid/util/ArrayMap;Landroid/util/SparseArray;Ljava/util/Map;)Lcom/vk/dto/newsfeed/entries/PromoPost;`
3. **Entry Factory and List Builder Caller Analysis**: Verification of `null` return handling when returning `const/4 v0, 0x0` / `return-object v0`.
4. **Catalog Ad Banner DTO and Parcelable Creator**: `Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;` and `Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto$a;`.

---

## 2. MyTarget Native Ad Entry Parser

### Descriptor
`Lcom/vk/dto/newsfeed/entries/MyTargetNativeAdEntry$a;->a(Lorg/json/JSONObject;Lap/d;)Lcom/vk/dto/newsfeed/entries/MyTargetNativeAdEntry;`

### First 30 Instructions
```smali
.method public static a(Lorg/json/JSONObject;Lap/d;)Lcom/vk/dto/newsfeed/entries/MyTargetNativeAdEntry;
    .locals 48

    .line 1
    move-object/from16 v0, p1

    .line 2
    .line 3
    const-string v1, "ads"

    .line 4
    .line 5
    move-object/from16 v2, p0

    .line 6
    .line 7
    invoke-virtual {v2, v1}, Lorg/json/JSONObject;->getJSONArray(Ljava/lang/String;)Lorg/json/JSONArray;

    .line 8
    .line 9
    .line 10
    move-result-object v1

    .line 11
    const/4 v2, 0x0

    .line 12
    invoke-virtual {v1, v2}, Lorg/json/JSONArray;->getJSONObject(I)Lorg/json/JSONObject;

    .line 13
```

### String Constants
```text
['', 'VK_NEWS_FEED_INA', 'ad_data', 'ads', 'bannerID', 'bls', 'carousel', 'format', 'multiple_answers', 'one_answer', 'pattern', 'pattern_type', 'sdk_source', 'static', 'template_id', 'type', 'video']
```

### Opcodes Used
```text
['check-cast', 'const', 'const-string', 'const/16', 'const/4', 'filled-new-array', 'goto', 'goto/16', 'if-eq', 'if-eqz', 'if-nez', 'iget', 'iget-object', 'invoke-direct', 'invoke-direct/range', 'invoke-interface', 'invoke-interface/range', 'invoke-static', 'invoke-static/range', 'invoke-virtual', 'iput-boolean', 'iput-object', 'move', 'move-object', 'move-object/from16', 'move-result', 'move-result-object', 'move/from16', 'new-instance', 'packed-switch', 'return-object', 'sget-object']
```

### Full Un-elided Smali Body
```smali
.method public static a(Lorg/json/JSONObject;Lap/d;)Lcom/vk/dto/newsfeed/entries/MyTargetNativeAdEntry;
    .locals 48

    .line 1
    move-object/from16 v0, p1

    .line 2
    .line 3
    const-string v1, "ads"

    .line 4
    .line 5
    move-object/from16 v2, p0

    .line 6
    .line 7
    invoke-virtual {v2, v1}, Lorg/json/JSONObject;->getJSONArray(Ljava/lang/String;)Lorg/json/JSONArray;

    .line 8
    .line 9
    .line 10
    move-result-object v1

    .line 11
    const/4 v2, 0x0

    .line 12
    invoke-virtual {v1, v2}, Lorg/json/JSONArray;->getJSONObject(I)Lorg/json/JSONObject;

    .line 13
    .line 14
    .line 15
    move-result-object v1

    .line 16
    const-string v3, "bannerID"

    .line 17
    .line 18
    invoke-virtual {v1, v3}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    .line 19
    .line 20
    .line 21
    move-result-object v5

    .line 22
    const-string v3, "pattern_type"

    .line 23
    .line 24
    invoke-virtual {v1, v3}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    .line 25
    .line 26
    .line 27
    move-result-object v6

    .line 28
    const-string v3, "template_id"

    .line 29
    .line 30
    invoke-virtual {v1, v3}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    .line 31
    .line 32
    .line 33
    move-result-object v7

    .line 34
    const-string v3, "pattern"

    .line 35
    .line 36
    invoke-virtual {v1, v3}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    .line 37
    .line 38
    .line 39
    move-result-object v8

    .line 40
    const-string v3, "sdk_source"

    .line 41
    .line 42
    invoke-virtual {v1, v3}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    .line 43
    .line 44
    .line 45
    move-result-object v10

    .line 46
    const-string v3, "type"

    .line 47
    .line 48
    invoke-virtual {v1, v3}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    .line 49
    .line 50
    .line 51
    move-result-object v12

    .line 52
    const-string v3, "format"

    .line 53
    .line 54
    invoke-virtual {v1, v3}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    .line 55
    .line 56
    .line 57
    move-result-object v11

    .line 58
    const-string v3, "ad_data"

    .line 59
    .line 60
    invoke-virtual {v1, v3}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    .line 61
    .line 62
    .line 63
    move-result-object v9

    .line 64
    const-string v1, "static"

    .line 65
    .line 66
    invoke-static {v11, v1}, Lkotlin/jvm/internal/h;->e(Ljava/lang/Object;Ljava/lang/Object;)Z

    .line 67
    .line 68
    .line 69
    move-result v3

    .line 70
    const-string v4, "video"

    .line 71
    .line 72
    invoke-static {v11, v4}, Lkotlin/jvm/internal/h;->e(Ljava/lang/Object;Ljava/lang/Object;)Z

    .line 73
    .line 74
    .line 75
    move-result v13

    .line 76
    if-eqz v13, :cond_0

    .line 77
    .line 78
    sget-object v13, Lcom/vk/toggle/b;->A:Lcom/vk/toggle/b;

    .line 79
    .line 80
    sget-object v15, Lcom/vk/toggle/features/FeedFeatures;->ADS_SDK_INTERNAL_NATIVE_AD_VIDEO:Lcom/vk/toggle/features/FeedFeatures;

    .line 81
    .line 82
    invoke-virtual {v13, v15}, Lcom/vk/toggle/internal/ToggleManager;->c(Lcom/vk/toggle/b$a;)Z

    .line 83
    .line 84
    .line 85
    move-result v13

    .line 86
    if-eqz v13, :cond_0

    .line 87
    .line 88
    const/4 v13, 0x1

    .line 89
    goto :goto_0

    .line 90
    :cond_0
    move v13, v2

    .line 91
    :goto_0
    const-string v15, "carousel"

    .line 92
    .line 93
    invoke-static {v11, v15}, Lkotlin/jvm/internal/h;->e(Ljava/lang/Object;Ljava/lang/Object;)Z

    .line 94
    .line 95
    .line 96
    move-result v16

    .line 97
    const-string v2, "bls"

    .line 98
    .line 99
    invoke-static {v11, v2}, Lkotlin/jvm/internal/h;->e(Ljava/lang/Object;Ljava/lang/Object;)Z

    .line 100
    .line 101
    .line 102
    move-result v17

    .line 103
    if-eqz v17, :cond_1

    .line 104
    .line 105
    sget-object v14, Lcom/vk/toggle/b;->A:Lcom/vk/toggle/b;

    .line 106
    .line 107
    move/from16 v18, v3

    .line 108
    .line 109
    sget-object v3, Lcom/vk/toggle/features/FeedFeatures;->ADS_SDK_INTERNAL_NATIVE_AD_BLS:Lcom/vk/toggle/features/FeedFeatures;

    .line 110
    .line 111
    invoke-virtual {v14, v3}, Lcom/vk/toggle/internal/ToggleManager;->c(Lcom/vk/toggle/b$a;)Z

    .line 112
    .line 113
    .line 114
    move-result v3

    .line 115
    if-eqz v3, :cond_2

    .line 116
    .line 117
    const/4 v3, 0x1

    .line 118
    goto :goto_1

    .line 119
    :cond_1
    move/from16 v18, v3

    .line 120
    .line 121
    :cond_2
    const/4 v3, 0x0

    .line 122
    :goto_1
    if-nez v18, :cond_4

    .line 123
    .line 124
    if-nez v13, :cond_4

    .line 125
    .line 126
    if-nez v16, :cond_4

    .line 127
    .line 128
    if-eqz v3, :cond_3

    .line 129
    .line 130
    goto :goto_2

    .line 131
    :cond_3
    const/4 v0, 0x0

    .line 132
    goto :goto_3

    .line 133
    :cond_4
    :goto_2
    if-eqz v0, :cond_3

    .line 134
    .line 135
    const-string v3, "VK_NEWS_FEED_INA"

    .line 136
    .line 137
    invoke-interface {v0, v10, v3}, Lap/d;->a(Ljava/lang/String;Ljava/lang/String;)Lmo/b5;

    .line 138
    .line 139
    .line 140
    move-result-object v0

    .line 141
    :goto_3
    invoke-static {v11, v1}, Lkotlin/jvm/internal/h;->e(Ljava/lang/Object;Ljava/lang/Object;)Z

    .line 142
    .line 143
    .line 144
    move-result v3

    .line 145
    if-eqz v3, :cond_5

    .line 146
    .line 147
    sget-object v3, Lcom/vk/toggle/b;->A:Lcom/vk/toggle/b;

    .line 148
    .line 149
    sget-object v13, Lcom/vk/toggle/features/FeedFeatures;->ADS_SDK_INTERNAL_NATIVE_AD_STATIC_MULTIPART_HOLDER:Lcom/vk/toggle/features/FeedFeatures;

    .line 150
    .line 151
    invoke-virtual {v3, v13}, Lcom/vk/toggle/internal/ToggleManager;->c(Lcom/vk/toggle/b$a;)Z

    .line 152
    .line 153
    .line 154
    move-result v3

    .line 155
    if-eqz v3, :cond_5

    .line 156
    .line 157
    const/4 v3, 0x1

    .line 158
    goto :goto_4

    .line 159
    :cond_5
    const/4 v3, 0x0

    .line 160
    :goto_4
    invoke-static {v11, v4}, Lkotlin/jvm/internal/h;->e(Ljava/lang/Object;Ljava/lang/Object;)Z

    .line 161
    .line 162
    .line 163
    move-result v13

    .line 164
    if-eqz v13, :cond_6

    .line 165
    .line 166
    sget-object v13, Lcom/vk/toggle/b;->A:Lcom/vk/toggle/b;

    .line 167
    .line 168
    sget-object v14, Lcom/vk/toggle/features/FeedFeatures;->ADS_SDK_INTERNAL_NATIVE_AD_VIDEO:Lcom/vk/toggle/features/FeedFeatures;

    .line 169
    .line 170
    invoke-virtual {v13, v14}, Lcom/vk/toggle/internal/ToggleManager;->c(Lcom/vk/toggle/b$a;)Z

    .line 171
    .line 172
    .line 173
    move-result v14

    .line 174
    if-eqz v14, :cond_6

    .line 175
    .line 176
    sget-object v14, Lcom/vk/toggle/features/FeedFeatures;->ADS_SDK_INTERNAL_NATIVE_AD_VIDEO_MULTIPART_HOLDER:Lcom/vk/toggle/features/FeedFeatures;

    .line 177
    .line 178
    invoke-virtual {v13, v14}, Lcom/vk/toggle/internal/ToggleManager;->c(Lcom/vk/toggle/b$a;)Z

    .line 179
    .line 180
    .line 181
    move-result v13

    .line 182
    if-eqz v13, :cond_6

    .line 183
    .line 184
    const/4 v13, 0x1

    .line 185
    goto :goto_5

    .line 186
    :cond_6
    const/4 v13, 0x0

    .line 187
    :goto_5
    invoke-static {v11, v15}, Lkotlin/jvm/internal/h;->e(Ljava/lang/Object;Ljava/lang/Object;)Z

    .line 188
    .line 189
    .line 190
    move-result v14

    .line 191
    if-eqz v14, :cond_7

    .line 192
    .line 193
    sget-object v14, Lcom/vk/toggle/b;->A:Lcom/vk/toggle/b;

    .line 194
    .line 195
    move/from16 p1, v3

    .line 196
    .line 197
    sget-object v3, Lcom/vk/toggle/features/FeedFeatures;->ADS_SDK_INTERNAL_NATIVE_AD_CAROUSEL_MULTIPART_HOLDER:Lcom/vk/toggle/features/FeedFeatures;

    .line 198
    .line 199
    invoke-virtual {v14, v3}, Lcom/vk/toggle/internal/ToggleManager;->c(Lcom/vk/toggle/b$a;)Z

    .line 200
    .line 201
    .line 202
    move-result v3

    .line 203
    if-eqz v3, :cond_8

    .line 204
    .line 205
    const/4 v3, 0x1

    .line 206
    goto :goto_6

    .line 207
    :cond_7
    move/from16 p1, v3

    .line 208
    .line 209
    :cond_8
    const/4 v3, 0x0

    .line 210
    :goto_6
    invoke-static {v11, v2}, Lkotlin/jvm/internal/h;->e(Ljava/lang/Object;Ljava/lang/Object;)Z

    .line 211
    .line 212
    .line 213
    move-result v14

    .line 214
    if-eqz v14, :cond_9

    .line 215
    .line 216
    sget-object v14, Lcom/vk/toggle/b;->A:Lcom/vk/toggle/b;

    .line 217
    .line 218
    move/from16 v18, v3

    .line 219
    .line 220
    sget-object v3, Lcom/vk/toggle/features/FeedFeatures;->ADS_SDK_INTERNAL_NATIVE_AD_BLS:Lcom/vk/toggle/features/FeedFeatures;

    .line 221
    .line 222
    invoke-virtual {v14, v3}, Lcom/vk/toggle/internal/ToggleManager;->c(Lcom/vk/toggle/b$a;)Z

    .line 223
    .line 224
    .line 225
    move-result v3

    .line 226
    if-eqz v3, :cond_a

    .line 227
    .line 228
    const/4 v3, 0x1

    .line 229
    goto :goto_7

    .line 230
    :cond_9
    move/from16 v18, v3

    .line 231
    .line 232
    :cond_a
    const/4 v3, 0x0

    .line 233
    :goto_7
    if-nez p1, :cond_c

    .line 234
    .line 235
    if-nez v13, :cond_c

    .line 236
    .line 237
    if-nez v18, :cond_c

    .line 238
    .line 239
    if-eqz v3, :cond_b

    .line 240
    .line 241
    goto :goto_8

    .line 242
    :cond_b
    const/16 v21, 0x0

    .line 243
    .line 244
    goto :goto_9

    .line 245
    :cond_c
    :goto_8
    const/16 v21, 0x1

    .line 246
    .line 247
    :goto_9
    if-eqz v21, :cond_14

    .line 248
    .line 249
    if-eqz v0, :cond_14

    .line 250
    .line 251
    iget-object v14, v0, Lmo/b5;->d:Lmo/ec;

    .line 252
    .line 253
    if-eqz v14, :cond_d

    .line 254
    .line 255
    iget-object v3, v14, Lmo/ec;->d:Lmo/w7;

    .line 256
    .line 257
    if-eqz v3, :cond_d

    .line 258
    .line 259
    new-instance v22, Lcom/vk/dto/newsfeed/Owner;

    .line 260
    .line 261
    iget-object v3, v3, Lmo/w7;->a:Lqo/b;

    .line 262
    .line 263
    iget-object v3, v3, Lmo/yb;->a:Ljava/lang/String;

    .line 264
    .line 265
    const v45, 0x2fffff

    .line 266
    .line 267
    .line 268
    const/16 v46, 0x0

    .line 269
    .line 270
    const/16 v23, 0x0

    .line 271
    .line 272
    const/16 v24, 0x0

    .line 273
    .line 274
    const/16 v25, 0x0

    .line 275
    .line 276
    const/16 v26, 0x0

    .line 277
    .line 278
    const/16 v27, 0x0

    .line 279
    .line 280
    const/16 v28, 0x0

    .line 281
    .line 282
    const/16 v29, 0x0

    .line 283
    .line 284
    const/16 v30, 0x0

    .line 285
    .line 286
    const/16 v31, 0x0

    .line 287
    .line 288
    const/16 v32, 0x0

    .line 289
    .line 290
    const/16 v33, 0x0

    .line 291
    .line 292
    const/16 v34, 0x0

    .line 293
    .line 294
    const/16 v35, 0x0

    .line 295
    .line 296
    const/16 v36, 0x0

    .line 297
    .line 298
    const/16 v37, 0x0

    .line 299
    .line 300
    const/16 v38, 0x0

    .line 301
    .line 302
    const/16 v39, 0x0

    .line 303
    .line 304
    const/16 v40, 0x0

    .line 305
    .line 306
    const/16 v41, 0x0

    .line 307
    .line 308
    const/16 v42, 0x0

    .line 309
    .line 310
    const/16 v44, 0x0

    .line 311
    .line 312
    move-object/from16 v43, v3

    .line 313
    .line 314
    invoke-direct/range {v22 .. v46}, Lcom/vk/dto/newsfeed/Owner;-><init>(Lcom/vk/dto/common/id/UserId;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lcom/vk/dto/common/VerifyInfo;Lcom/vk/dto/common/Image;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lcom/vk/dto/user/ImageStatus;Lcom/vk/dto/user/UserSex;Ljava/lang/String;Ljava/lang/String;ZZZZLcom/vk/dto/user/SocialButtonType;ILcom/vk/dto/common/FriendsInfo;Ljava/lang/String;ZILkotlin/jvm/internal/DefaultConstructorMarker;)V

    .line 315
    .line 316
    .line 317
    invoke-static/range {v22 .. v22}, Ljava/util/Collections;->singletonList(Ljava/lang/Object;)Ljava/util/List;

    .line 318
    .line 319
    .line 320
    move-result-object v3

    .line 321
    goto :goto_a

    .line 322
    :cond_d
    const/4 v3, 0x0

    .line 323
    :goto_a
    if-eqz v14, :cond_e

    .line 324
    .line 325
    iget-object v13, v14, Lmo/ec;->d:Lmo/w7;

    .line 326
    .line 327
    if-eqz v13, :cond_e

    .line 328
    .line 329
    sget-object v19, Lcom/vk/dto/common/Image;->CREATOR:Lcom/vk/core/serialize/Serializer$c;

    .line 330
    .line 331
    iget-object v13, v13, Lmo/w7;->a:Lqo/b;

    .line 332
    .line 333
    move-object/from16 v19, v5

    .line 334
    .line 335
    iget-object v5, v13, Lmo/yb;->a:Ljava/lang/String;

    .line 336
    .line 337
    move-object/from16 v20, v6

    .line 338
    .line 339
    iget v6, v13, Lmo/yb;->b:I

    .line 340
    .line 341
    iget v13, v13, Lmo/yb;->c:I

    .line 342
    .line 343
    move-object/from16 v22, v7

    .line 344
    .line 345
    const/16 v7, 0x30

    .line 346
    .line 347
    invoke-static {v7, v6, v13, v5}, Lcom/vk/dto/common/Image$b;->d(CIILjava/lang/String;)Lcom/vk/dto/common/Image;

    .line 348
    .line 349
    .line 350
    move-result-object v5

    .line 351
    goto :goto_b

    .line 352
    :cond_e
    move-object/from16 v19, v5

    .line 353
    .line 354
    move-object/from16 v20, v6

    .line 355
    .line 356
    move-object/from16 v22, v7

    .line 357
    .line 358
    const/4 v5, 0x0

    .line 359
    :goto_b
    if-eqz v14, :cond_f

    .line 360
    .line 361
    iget-object v6, v14, Lmo/ec;->d:Lmo/w7;

    .line 362
    .line 363
    if-eqz v6, :cond_f

    .line 364
    .line 365
    iget-object v6, v6, Lmo/w7;->a:Lqo/b;

    .line 366
    .line 367
    iget-object v6, v6, Lmo/yb;->a:Ljava/lang/String;

    .line 368
    .line 369
    goto :goto_c

    .line 370
    :cond_f
    const/4 v6, 0x0

    .line 371
    :goto_c
    new-instance v7, Lcom/vk/feed/core/models/header/SourcePhoto;

    .line 372
    .line 373
    invoke-direct {v7, v3, v5, v6}, Lcom/vk/feed/core/models/header/SourcePhoto;-><init>(Ljava/util/List;Lcom/vk/dto/common/Image;Ljava/lang/String;)V

    .line 374
    .line 375
    .line 376
    if-eqz v14, :cond_10

    .line 377
    .line 378
    iget-object v3, v14, Lmo/ec;->a:Lmo/g8;

    .line 379
    .line 380
    iget-object v3, v3, Lmo/x;->f:Ljava/lang/String;

    .line 381
    .line 382
    if-eqz v3, :cond_10

    .line 383
    .line 384
    new-instance v5, Lcom/vk/feed/core/models/Text;

    .line 385
    .line 386
    const/4 v6, 0x2

    .line 387
    const/4 v13, 0x0

    .line 388
    invoke-direct {v5, v3, v13, v6, v13}, Lcom/vk/feed/core/models/Text;-><init>(Ljava/lang/String;Lcom/vk/dto/common/ThemedColor;ILkotlin/jvm/internal/DefaultConstructorMarker;)V

    .line 389
    .line 390
    .line 391
    invoke-static {v5}, Ljava/util/Collections;->singletonList(Ljava/lang/Object;)Ljava/util/List;

    .line 392
    .line 393
    .line 394
    move-result-object v3

    .line 395
    move-object/from16 v25, v3

    .line 396
    .line 397
    goto :goto_d

    .line 398
    :cond_10
    const/4 v13, 0x0

    .line 399
    move-object/from16 v25, v13

    .line 400
    .line 401
    :goto_d
    new-instance v26, Lcom/vk/dto/common/VerifyInfo;

    .line 402
    .line 403
    const/16 v33, 0x3f

    .line 404
    .line 405
    const/16 v34, 0x0

    .line 406
    .line 407
    const/16 v27, 0x0

    .line 408
    .line 409
    const/16 v28, 0x0

    .line 410
    .line 411
    const/16 v29, 0x0

    .line 412
    .line 413
    const/16 v30, 0x0

    .line 414
    .line 415
    const/16 v31, 0x0

    .line 416
    .line 417
    const/16 v32, 0x0

    .line 418
    .line 419
    invoke-direct/range {v26 .. v34}, Lcom/vk/dto/common/VerifyInfo;-><init>(ZZZZZZILkotlin/jvm/internal/DefaultConstructorMarker;)V

    .line 420
    .line 421
    .line 422
    new-instance v23, Lcom/vk/feed/core/models/header/HeaderTitle;

    .line 423
    .line 424
    const/16 v24, 0x0

    .line 425
    .line 426
    move-object/from16 v27, v26

    .line 427
    .line 428
    const/16 v26, 0x0

    .line 429
    .line 430
    const/16 v31, 0x60

    .line 431
    .line 432
    const/16 v32, 0x0

    .line 433
    .line 434
    invoke-direct/range {v23 .. v32}, Lcom/vk/feed/core/models/header/HeaderTitle;-><init>(Ljava/util/List;Ljava/util/List;Lcom/vk/dto/user/ImageStatus;Lcom/vk/dto/common/VerifyInfo;ZZZILkotlin/jvm/internal/DefaultConstructorMarker;)V

    .line 435
    .line 436
    .line 437
    if-eqz v14, :cond_11

    .line 438
    .line 439
    iget-object v3, v14, Lmo/ec;->a:Lmo/g8;

    .line 440
    .line 441
    iget-object v3, v3, Lmo/x;->h:Ljava/lang/String;

    .line 442
    .line 443
    goto :goto_e

    .line 444
    :cond_11
    move-object v3, v13

    .line 445
    :goto_e
    invoke-static {v3}, Lmb2/b;->a(Ljava/lang/String;)Lcom/vk/feed/core/models/Description;

    .line 446
    .line 447
    .line 448
    move-result-object v3

    .line 449
    if-eqz v14, :cond_12

    .line 450
    .line 451
    iget-object v5, v14, Lmo/ec;->a:Lmo/g8;

    .line 452
    .line 453
    iget-object v5, v5, Lmo/x;->q:Ljava/lang/String;

    .line 454
    .line 455
    goto :goto_f

    .line 456
    :cond_12
    move-object v5, v13

    .line 457
    :goto_f
    invoke-static {v5}, Lmb2/b;->a(Ljava/lang/String;)Lcom/vk/feed/core/models/Description;

    .line 458
    .line 459
    .line 460
    move-result-object v5

    .line 461
    if-eqz v14, :cond_13

    .line 462
    .line 463
    iget-object v6, v14, Lmo/ec;->a:Lmo/g8;

    .line 464
    .line 465
    iget-object v6, v6, Lmo/x;->n:Ljava/lang/String;

    .line 466
    .line 467
    if-eqz v6, :cond_13

    .line 468
    .line 469
    invoke-static {v6}, Lcx1/t0;->a(Ljava/lang/CharSequence;)Z

    .line 470
    .line 471
    .line 472
    move-result v14

    .line 473
    if-eqz v14, :cond_13

    .line 474
    .line 475
    goto :goto_10

    .line 476
    :cond_13
    move-object v6, v13

    .line 477
    :goto_10
    invoke-static {v6}, Lmb2/b;->a(Ljava/lang/String;)Lcom/vk/feed/core/models/Description;

    .line 478
    .line 479
    .line 480
    move-result-object v6

    .line 481
    filled-new-array {v3, v5, v6}, [Lcom/vk/feed/core/models/Description;

    .line 482
    .line 483
    .line 484
    move-result-object v3

    .line 485
    invoke-static {v3}, Lkotlin/collections/m;->J([Ljava/lang/Object;)Ljava/util/List;

    .line 486
    .line 487
    .line 488
    move-result-object v27

    .line 489
    move-object/from16 v25, v23

    .line 490
    .line 491
    new-instance v23, Lcom/vk/feed/core/models/header/EntryHeader;

    .line 492
    .line 493
    const/16 v26, 0x0

    .line 494
    .line 495
    const/16 v28, 0x0

    .line 496
    .line 497
    const/16 v29, 0x0

    .line 498
    .line 499
    const/16 v30, 0x0

    .line 500
    .line 501
    const/16 v31, 0x0

    .line 502
    .line 503
    const/16 v32, 0x0

    .line 504
    .line 505
    move-object/from16 v24, v7

    .line 506
    .line 507
    invoke-direct/range {v23 .. v32}, Lcom/vk/feed/core/models/header/EntryHeader;-><init>(Lcom/vk/feed/core/models/header/SourcePhoto;Lcom/vk/feed/core/models/header/HeaderTitle;Lcom/vk/feed/core/models/header/HeaderBadge;Ljava/util/List;Lcom/vk/feed/core/models/OverlayImage;Ljava/lang/String;Ljava/lang/Integer;Lcom/vk/dto/user/SocialButtonType;Lcom/vk/feed/core/models/header/HeaderIcon;)V

    .line 508
    .line 509
    .line 510
    goto :goto_11

    .line 511
    :cond_14
    move-object/from16 v19, v5

    .line 512
    .line 513
    move-object/from16 v20, v6

    .line 514
    .line 515
    move-object/from16 v22, v7

    .line 516
    .line 517
    const/4 v13, 0x0

    .line 518
    move-object/from16 v23, v13

    .line 519
    .line 520
    :goto_11
    if-eqz v0, :cond_15

    .line 521
    .line 522
    iget-object v3, v0, Lmo/b5;->d:Lmo/ec;

    .line 523
    .line 524
    if-eqz v3, :cond_15

    .line 525
    .line 526
    iget-object v3, v3, Lmo/ec;->e:Lmo/w7;

    .line 527
    .line 528
    if-eqz v3, :cond_15

    .line 529
    .line 530
    sget-object v5, Lcom/vk/dto/common/Image;->CREATOR:Lcom/vk/core/serialize/Serializer$c;

    .line 531
    .line 532
    iget-object v3, v3, Lmo/w7;->a:Lqo/b;

    .line 533
    .line 534
    iget-object v5, v3, Lmo/yb;->a:Ljava/lang/String;

    .line 535
    .line 536
    iget v6, v3, Lmo/yb;->b:I

    .line 537
    .line 538
    iget v3, v3, Lmo/yb;->c:I

    .line 539
    .line 540
    const/16 v7, 0x30

    .line 541
    .line 542
    invoke-static {v7, v6, v3, v5}, Lcom/vk/dto/common/Image$b;->d(CIILjava/lang/String;)Lcom/vk/dto/common/Image;

    .line 543
    .line 544
    .line 545
    move-result-object v3

    .line 546
    new-instance v5, Lcom/vkontakte/android/attachments/PhotoAttachment;

    .line 547
    .line 548
    new-instance v6, Lcom/vk/dto/photo/Photo;

    .line 549
    .line 550
    invoke-direct {v6, v3}, Lcom/vk/dto/photo/Photo;-><init>(Lcom/vk/dto/common/Image;)V

    .line 551
    .line 552
    .line 553
    invoke-direct {v5, v6}, Lcom/vkontakte/android/attachments/PhotoAttachment;-><init>(Lcom/vk/dto/photo/Photo;)V

    .line 554
    .line 555
    .line 556
    const/4 v3, 0x1

    .line 557
    iput-boolean v3, v5, Lcom/vk/dto/common/Attachment;->a:Z

    .line 558
    .line 559
    invoke-virtual {v11, v1}, Ljava/lang/Object;->equals(Ljava/lang/Object;)Z

    .line 560
    .line 561
    .line 562
    move-result v3

    .line 563
    if-eqz v3, :cond_15

    .line 564
    .line 565
    move-object/from16 v16, v5

    .line 566
    .line 567
    goto :goto_12

    .line 568
    :cond_15
    move-object/from16 v16, v13

    .line 569
    .line 570
    :goto_12
    if-eqz v0, :cond_22

    .line 571
    .line 572
    iget-object v3, v0, Lmo/b5;->d:Lmo/ec;

    .line 573
    .line 574
    if-eqz v3, :cond_22

    .line 575
    .line 576
    iget-object v3, v3, Lmo/ec;->a:Lmo/g8;

    .line 577
    .line 578
    iget-object v3, v3, Lmo/g8;->a0:Lmg/l;

    .line 579
    .line 580
    if-eqz v3, :cond_22

    .line 581
    .line 582
    new-instance v5, Ljava/util/ArrayList;

    .line 583
    .line 584
    iget-object v3, v3, Lmg/l;->c:Ljava/lang/Object;

    .line 585
    .line 586
    check-cast v3, Ljava/util/ArrayList;

    .line 587
    .line 588
    invoke-direct {v5, v3}, Ljava/util/ArrayList;-><init>(Ljava/util/Collection;)V

    .line 589
    .line 590
    .line 591
    new-instance v3, Ljava/util/ArrayList;

    .line 592
    .line 593
    const/16 v6, 0xa

    .line 594
    .line 595
    invoke-static {v5, v6}, Lkotlin/collections/p;->v(Ljava/lang/Iterable;I)I

    .line 596
    .line 597
    .line 598
    move-result v7

    .line 599
    invoke-direct {v3, v7}, Ljava/util/ArrayList;-><init>(I)V

    .line 600
    .line 601
    .line 602
    invoke-virtual {v5}, Ljava/util/ArrayList;->iterator()Ljava/util/Iterator;

    .line 603
    .line 604
    .line 605
    move-result-object v5

    .line 606
    :goto_13
    invoke-interface {v5}, Ljava/util/Iterator;->hasNext()Z

    .line 607
    .line 608
    .line 609
    move-result v7

    .line 610
    if-eqz v7, :cond_1f

    .line 611
    .line 612
    invoke-interface {v5}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    .line 613
    .line 614
    .line 615
    move-result-object v7

    .line 616
    check-cast v7, Lep/b;

    .line 617
    .line 618
    invoke-interface {v7}, Lep/b;->n()Ljava/lang/String;

    .line 619
    .line 620
    .line 621
    move-result-object v25

    .line 622
    invoke-interface {v7}, Lep/b;->getText()Ljava/lang/String;

    .line 623
    .line 624
    .line 625
    move-result-object v26

    .line 626
    invoke-interface {v7}, Lep/b;->a1()Ljava/lang/String;

    .line 627
    .line 628
    .line 629
    move-result-object v14

    .line 630
    const-string v13, "multiple_answers"

    .line 631
    .line 632
    invoke-static {v14, v13}, Lkotlin/jvm/internal/h;->e(Ljava/lang/Object;Ljava/lang/Object;)Z

    .line 633
    .line 634
    .line 635
    move-result v14

    .line 636
    if-eqz v14, :cond_16

    .line 637
    .line 638
    :goto_14
    move-object/from16 v27, v13

    .line 639
    .line 640
    goto :goto_15

    .line 641
    :cond_16
    const-string v13, "one_answer"

    .line 642
    .line 643
    goto :goto_14

    .line 644
    :goto_15
    invoke-interface {v7}, Lep/b;->b1()Ljava/util/ArrayList;

    .line 645
    .line 646
    .line 647
    move-result-object v13

    .line 648
    new-instance v14, Ljava/util/ArrayList;

    .line 649
    .line 650
    move-object/from16 v31, v5

    .line 651
    .line 652
    invoke-static {v13, v6}, Lkotlin/collections/p;->v(Ljava/lang/Iterable;I)I

    .line 653
    .line 654
    .line 655
    move-result v5

    .line 656
    invoke-direct {v14, v5}, Ljava/util/ArrayList;-><init>(I)V

    .line 657
    .line 658
    .line 659
    invoke-interface {v13}, Ljava/lang/Iterable;->iterator()Ljava/util/Iterator;

    .line 660
    .line 661
    .line 662
    move-result-object v5

    .line 663
    :goto_16
    invoke-interface {v5}, Ljava/util/Iterator;->hasNext()Z

    .line 664
    .line 665
    .line 666
    move-result v13

    .line 667
    if-eqz v13, :cond_1c

    .line 668
    .line 669
    invoke-interface {v5}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    .line 670
    .line 671
    .line 672
    move-result-object v13

    .line 673
    check-cast v13, Lep/a;

    .line 674
    .line 675
    invoke-interface {v13}, Lep/a;->getId()Ljava/lang/String;

    .line 676
    .line 677
    .line 678
    move-result-object v6

    .line 679
    move-object/from16 v24, v5

    .line 680
    .line 681
    invoke-interface {v13}, Lep/a;->getType()I

    .line 682
    .line 683
    .line 684
    move-result v5

    .line 685
    move-object/from16 v28, v7

    .line 686
    .line 687
    if-eqz v5, :cond_19

    .line 688
    .line 689
    const/4 v7, 0x1

    .line 690
    if-eq v5, v7, :cond_18

    .line 691
    .line 692
    const/4 v7, 0x2

    .line 693
    if-eq v5, v7, :cond_17

    .line 694
    .line 695
    :goto_17
    const/4 v5, 0x0

    .line 696
    goto :goto_18

    .line 697
    :cond_17
    move v5, v7

    .line 698
    goto :goto_18

    .line 699
    :cond_18
    const/4 v7, 0x2

    .line 700
    const/4 v5, 0x1

    .line 701
    goto :goto_18

    .line 702
    :cond_19
    const/4 v7, 0x2

    .line 703
    goto :goto_17

    .line 704
    :goto_18
    invoke-interface {v13}, Lep/a;->a()Ljava/util/List;

    .line 705
    .line 706
    .line 707
    move-result-object v29

    .line 708
    if-eqz v29, :cond_1b

    .line 709
    .line 710
    move-object/from16 v7, v29

    .line 711
    .line 712
    check-cast v7, Ljava/lang/Iterable;

    .line 713
    .line 714
    move-object/from16 v33, v8

    .line 715
    .line 716
    new-instance v8, Ljava/util/ArrayList;

    .line 717
    .line 718
    move-object/from16 v34, v9

    .line 719
    .line 720
    move-object/from16 v35, v10

    .line 721
    .line 722
    const/16 v9, 0xa

    .line 723
    .line 724
    invoke-static {v7, v9}, Lkotlin/collections/p;->v(Ljava/lang/Iterable;I)I

    .line 725
    .line 726
    .line 727
    move-result v10

    .line 728
    invoke-direct {v8, v10}, Ljava/util/ArrayList;-><init>(I)V

    .line 729
    .line 730
    .line 731
    invoke-interface {v7}, Ljava/lang/Iterable;->iterator()Ljava/util/Iterator;

    .line 732
    .line 733
    .line 734
    move-result-object v7

    .line 735
    :goto_19
    invoke-interface {v7}, Ljava/util/Iterator;->hasNext()Z

    .line 736
    .line 737
    .line 738
    move-result v9

    .line 739
    if-eqz v9, :cond_1a

    .line 740
    .line 741
    invoke-interface {v7}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    .line 742
    .line 743
    .line 744
    move-result-object v9

    .line 745
    check-cast v9, Lcp/a;

    .line 746
    .line 747
    sget-object v10, Lcom/vk/dto/common/Image;->CREATOR:Lcom/vk/core/serialize/Serializer$c;

    .line 748
    .line 749
    invoke-interface {v9}, Lcp/a;->getUrl()Ljava/lang/String;

    .line 750
    .line 751
    .line 752
    move-result-object v10

    .line 753
    move-object/from16 v29, v7

    .line 754
    .line 755
    invoke-interface {v9}, Lcp/a;->getWidth()I

    .line 756
    .line 757
    .line 758
    move-result v7

    .line 759
    invoke-interface {v9}, Lcp/a;->getHeight()I

    .line 760
    .line 761
    .line 762
    move-result v9

    .line 763
    move-object/from16 v36, v12

    .line 764
    .line 765
    const/16 v12, 0x30

    .line 766
    .line 767
    invoke-static {v12, v7, v9, v10}, Lcom/vk/dto/common/Image$b;->d(CIILjava/lang/String;)Lcom/vk/dto/common/Image;

    .line 768
    .line 769
    .line 770
    move-result-object v7

    .line 771
    invoke-virtual {v8, v7}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    .line 772
    .line 773
    .line 774
    move-object/from16 v7, v29

    .line 775
    .line 776
    move-object/from16 v12, v36

    .line 777
    .line 778
    goto :goto_19

    .line 779
    :cond_1a
    move-object/from16 v36, v12

    .line 780
    .line 781
    goto :goto_1a

    .line 782
    :cond_1b
    move-object/from16 v33, v8

    .line 783
    .line 784
    move-object/from16 v34, v9

    .line 785
    .line 786
    move-object/from16 v35, v10

    .line 787
    .line 788
    move-object/from16 v36, v12

    .line 789
    .line 790
    sget-object v8, Lkotlin/collections/EmptyList;->a:Lkotlin/collections/EmptyList;

    .line 791
    .line 792
    :goto_1a
    invoke-interface {v13}, Lep/a;->getText()Ljava/lang/String;

    .line 793
    .line 794
    .line 795
    move-result-object v7

    .line 796
    new-instance v9, Lcom/vk/feed/core/models/MyTargetBrandLiftSurvey$Answer;

    .line 797
    .line 798
    invoke-direct {v9, v6, v5, v8, v7}, Lcom/vk/feed/core/models/MyTargetBrandLiftSurvey$Answer;-><init>(Ljava/lang/String;ILjava/util/List;Ljava/lang/String;)V

    .line 799
    .line 800
    .line 801
    invoke-virtual {v14, v9}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    .line 802
    .line 803
    .line 804
    move-object/from16 v5, v24

    .line 805
    .line 806
    move-object/from16 v7, v28

    .line 807
    .line 808
    move-object/from16 v8, v33

    .line 809
    .line 810
    move-object/from16 v9, v34

    .line 811
    .line 812
    move-object/from16 v10, v35

    .line 813
    .line 814
    move-object/from16 v12, v36

    .line 815
    .line 816
    const/16 v6, 0xa

    .line 817
    .line 818
    goto/16 :goto_16

    .line 819
    .line 820
    :cond_1c
    move-object/from16 v28, v7

    .line 821
    .line 822
    move-object/from16 v33, v8

    .line 823
    .line 824
    move-object/from16 v34, v9

    .line 825
    .line 826
    move-object/from16 v35, v10

    .line 827
    .line 828
    move-object/from16 v36, v12

    .line 829
    .line 830
    invoke-interface/range {v28 .. v28}, Lep/b;->Z0()Ljava/util/List;

    .line 831
    .line 832
    .line 833
    move-result-object v5

    .line 834
    if-eqz v5, :cond_1e

    .line 835
    .line 836
    check-cast v5, Ljava/lang/Iterable;

    .line 837
    .line 838
    new-instance v6, Ljava/util/ArrayList;

    .line 839
    .line 840
    const/16 v9, 0xa

    .line 841
    .line 842
    invoke-static {v5, v9}, Lkotlin/collections/p;->v(Ljava/lang/Iterable;I)I

    .line 843
    .line 844
    .line 845
    move-result v7

    .line 846
    invoke-direct {v6, v7}, Ljava/util/ArrayList;-><init>(I)V

    .line 847
    .line 848
    .line 849
    invoke-interface {v5}, Ljava/lang/Iterable;->iterator()Ljava/util/Iterator;

    .line 850
    .line 851
    .line 852
    move-result-object v5

    .line 853
    :goto_1b
    invoke-interface {v5}, Ljava/util/Iterator;->hasNext()Z

    .line 854
    .line 855
    .line 856
    move-result v7

    .line 857
    if-eqz v7, :cond_1d

    .line 858
    .line 859
    invoke-interface {v5}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    .line 860
    .line 861
    .line 862
    move-result-object v7

    .line 863
    check-cast v7, Lcp/a;

    .line 864
    .line 865
    sget-object v8, Lcom/vk/dto/common/Image;->CREATOR:Lcom/vk/core/serialize/Serializer$c;

    .line 866
    .line 867
    invoke-interface {v7}, Lcp/a;->getUrl()Ljava/lang/String;

    .line 868
    .line 869
    .line 870
    move-result-object v8

    .line 871
    invoke-interface {v7}, Lcp/a;->getWidth()I

    .line 872
    .line 873
    .line 874
    move-result v10

    .line 875
    invoke-interface {v7}, Lcp/a;->getHeight()I

    .line 876
    .line 877
    .line 878
    move-result v7

    .line 879
    const/16 v12, 0x30

    .line 880
    .line 881
    invoke-static {v12, v10, v7, v8}, Lcom/vk/dto/common/Image$b;->d(CIILjava/lang/String;)Lcom/vk/dto/common/Image;

    .line 882
    .line 883
    .line 884
    move-result-object v7

    .line 885
    invoke-virtual {v6, v7}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    .line 886
    .line 887
    .line 888
    goto :goto_1b

    .line 889
    :cond_1d
    const/16 v12, 0x30

    .line 890
    .line 891
    :goto_1c
    move-object/from16 v29, v6

    .line 892
    .line 893
    goto :goto_1d

    .line 894
    :cond_1e
    const/16 v9, 0xa

    .line 895
    .line 896
    const/16 v12, 0x30

    .line 897
    .line 898
    sget-object v6, Lkotlin/collections/EmptyList;->a:Lkotlin/collections/EmptyList;

    .line 899
    .line 900
    goto :goto_1c

    .line 901
    :goto_1d
    new-instance v24, Lcom/vk/feed/core/models/MyTargetBrandLiftSurvey$Question;

    .line 902
    .line 903
    move-object/from16 v28, v14

    .line 904
    .line 905
    invoke-direct/range {v24 .. v29}, Lcom/vk/feed/core/models/MyTargetBrandLiftSurvey$Question;-><init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/util/List;Ljava/util/List;)V

    .line 906
    .line 907
    .line 908
    move-object/from16 v5, v24

    .line 909
    .line 910
    invoke-virtual {v3, v5}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    .line 911
    .line 912
    .line 913
    move v6, v9

    .line 914
    move-object/from16 v5, v31

    .line 915
    .line 916
    move-object/from16 v8, v33

    .line 917
    .line 918
    move-object/from16 v9, v34

    .line 919
    .line 920
    move-object/from16 v10, v35

    .line 921
    .line 922
    move-object/from16 v12, v36

    .line 923
    .line 924
    const/4 v13, 0x0

    .line 925
    goto/16 :goto_13

    .line 926
    .line 927
    :cond_1f
    move-object/from16 v33, v8

    .line 928
    .line 929
    move-object/from16 v34, v9

    .line 930
    .line 931
    move-object/from16 v35, v10

    .line 932
    .line 933
    move-object/from16 v36, v12

    .line 934
    .line 935
    invoke-virtual {v3}, Ljava/util/ArrayList;->isEmpty()Z

    .line 936
    .line 937
    .line 938
    move-result v5

    .line 939
    if-nez v5, :cond_20

    .line 940
    .line 941
    goto :goto_1e

    .line 942
    :cond_20
    const/4 v3, 0x0

    .line 943
    :goto_1e
    if-eqz v3, :cond_21

    .line 944
    .line 945
    new-instance v5, Lcom/vk/feed/core/models/MyTargetBrandLiftSurvey;

    .line 946
    .line 947
    invoke-direct {v5, v3}, Lcom/vk/feed/core/models/MyTargetBrandLiftSurvey;-><init>(Ljava/util/List;)V

    .line 948
    .line 949
    .line 950
    goto :goto_20

    .line 951
    :cond_21
    :goto_1f
    const/4 v5, 0x0

    .line 952
    goto :goto_20

    .line 953
    :cond_22
    move-object/from16 v33, v8

    .line 954
    .line 955
    move-object/from16 v34, v9

    .line 956
    .line 957
    move-object/from16 v35, v10

    .line 958
    .line 959
    move-object/from16 v36, v12

    .line 960
    .line 961
    goto :goto_1f

    .line 962
    :goto_20
    if-eqz v5, :cond_23

    .line 963
    .line 964
    invoke-virtual {v11, v2}, Ljava/lang/Object;->equals(Ljava/lang/Object;)Z

    .line 965
    .line 966
    .line 967
    move-result v2

    .line 968
    if-eqz v2, :cond_23

    .line 969
    .line 970
    move-object/from16 v18, v5

    .line 971
    .line 972
    goto :goto_21

    .line 973
    :cond_23
    const/16 v18, 0x0

    .line 974
    .line 975
    :goto_21
    if-eqz v0, :cond_24

    .line 976
    .line 977
    iget-object v2, v0, Lmo/b5;->d:Lmo/ec;

    .line 978
    .line 979
    if-eqz v2, :cond_24

    .line 980
    .line 981
    iget-object v2, v2, Lmo/ec;->a:Lmo/g8;

    .line 982
    .line 983
    iget-object v2, v2, Lmo/x;->c:Ljava/lang/String;

    .line 984
    .line 985
    goto :goto_22

    .line 986
    :cond_24
    const/4 v2, 0x0

    .line 987
    :goto_22
    if-eqz v2, :cond_25

    .line 988
    .line 989
    new-instance v37, Lx92/c$b;

    .line 990
    .line 991
    sget-object v40, Lrq1/d$c;->a:Lrq1/d$c;

    .line 992
    .line 993
    invoke-static {}, Lcom/vk/bridges/f;->a()Lcom/vk/bridges/a;

    .line 994
    .line 995
    .line 996
    move-result-object v3

    .line 997
    invoke-interface {v3}, Lcom/vk/bridges/a;->I()Lw50/b;

    .line 998
    .line 999
    .line 1000
    move-result-object v3

    .line 1001
    iget-object v3, v3, Lw50/b;->W:Lcom/vk/dto/user/ObsceneTextFilter;

    .line 1002
    .line 1003
    const/16 v46, 0x0

    .line 1004
    .line 1005
    const/16 v47, 0x1f3

    .line 1006
    .line 1007
    const/16 v38, 0x0

    .line 1008
    .line 1009
    const/16 v39, 0x0

    .line 1010
    .line 1011
    const/16 v42, 0x0

    .line 1012
    .line 1013
    const/16 v43, 0x0

    .line 1014
    .line 1015
    const/16 v44, 0x0

    .line 1016
    .line 1017
    const/16 v45, 0x0

    .line 1018
    .line 1019
    move-object/from16 v41, v3

    .line 1020
    .line 1021
    invoke-direct/range {v37 .. v47}, Lx92/c$b;-><init>(Landroid/os/Bundle;FLrq1/d;Lcom/vk/dto/user/ObsceneTextFilter;Lh12/o;IILsq1/f;Lcom/vk/common/links/LinksParserData$HashtagService;I)V

    .line 1022
    .line 1023
    .line 1024
    move-object/from16 v3, v37

    .line 1025
    .line 1026
    new-instance v5, Lx92/c$c;

    .line 1027
    .line 1028
    const/4 v6, 0x0

    .line 1029
    invoke-direct {v5, v6}, Lx92/c$c;-><init>(I)V

    .line 1030
    .line 1031
    .line 1032
    invoke-static {v2, v3, v5}, Lx92/c$a;->a(Ljava/lang/String;Lx92/c$b;Lx92/c$c;)Lx92/c;

    .line 1033
    .line 1034
    .line 1035
    move-result-object v2

    .line 1036
    goto :goto_23

    .line 1037
    :cond_25
    const/4 v2, 0x0

    .line 1038
    :goto_23
    invoke-virtual {v11}, Ljava/lang/String;->hashCode()I

    .line 1039
    .line 1040
    .line 1041
    move-result v3

    .line 1042
    const v5, -0x35323192    # -6743863.0f

    .line 1043
    .line 1044
    .line 1045
    if-eq v3, v5, :cond_28

    .line 1046
    .line 1047
    const v1, 0x2c6160

    .line 1048
    .line 1049
    .line 1050
    if-eq v3, v1, :cond_27

    .line 1051
    .line 1052
    const v1, 0x6b0147b

    .line 1053
    .line 1054
    .line 1055
    if-eq v3, v1, :cond_26

    .line 1056
    .line 1057
    goto :goto_24

    .line 1058
    :cond_26
    invoke-virtual {v11, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    .line 1059
    .line 1060
    .line 1061
    move-result v1

    .line 1062
    if-nez v1, :cond_2a

    .line 1063
    .line 1064
    goto :goto_24

    .line 1065
    :cond_27
    invoke-virtual {v11, v15}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    .line 1066
    .line 1067
    .line 1068
    move-result v1

    .line 1069
    if-eqz v1, :cond_29

    .line 1070
    .line 1071
    goto :goto_26

    .line 1072
    :cond_28
    invoke-virtual {v11, v1}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    .line 1073
    .line 1074
    .line 1075
    move-result v1

    .line 1076
    if-nez v1, :cond_2a

    .line 1077
    .line 1078
    :cond_29
    :goto_24
    sget-object v1, Lcom/vk/dto/newsfeed/entries/DisclaimerContent;->UNSUPPORTED:Lcom/vk/dto/newsfeed/entries/DisclaimerContent;

    .line 1079
    .line 1080
    :goto_25
    move-object v14, v1

    .line 1081
    goto :goto_27

    .line 1082
    :cond_2a
    :goto_26
    sget-object v1, Lcom/vk/dto/newsfeed/entries/DisclaimerContent;->TEXT_WITH_CONTENT:Lcom/vk/dto/newsfeed/entries/DisclaimerContent;

    .line 1083
    .line 1084
    goto :goto_25

    .line 1085
    :goto_27
    if-eqz v0, :cond_2b

    .line 1086
    .line 1087
    iget-object v1, v0, Lmo/b5;->d:Lmo/ec;

    .line 1088
    .line 1089
    if-eqz v1, :cond_2b

    .line 1090
    .line 1091
    invoke-virtual {v1}, Lmo/ec;->a()Lqo/a;

    .line 1092
    .line 1093
    .line 1094
    move-result-object v1

    .line 1095
    goto :goto_28

    .line 1096
    :cond_2b
    const/4 v1, 0x0

    .line 1097
    :goto_28
    if-eqz v1, :cond_2c

    .line 1098
    .line 1099
    iget v1, v1, Lqo/a;->a:I

    .line 1100
    .line 1101
    const v3, 0x3dcccccd    # 0.1f

    .line 1102
    .line 1103
    .line 1104
    const v4, 0x3d8f5c29    # 0.07f

    .line 1105
    .line 1106
    .line 1107
    const/16 v6, 0x50

    .line 1108
    .line 1109
    packed-switch v1, :pswitch_data_0

    .line 1110
    .line 1111
    .line 1112
    :pswitch_0
    const/4 v3, 0x0

    .line 1113
    const/4 v1, 0x0

    .line 1114
    const/4 v6, 0x0

    .line 1115
    goto :goto_2a

    .line 1116
    :pswitch_1
    sget-object v1, Lcom/vkontakte/android/attachments/DisclaimerType;->ENERGETICS:Lcom/vkontakte/android/attachments/DisclaimerType;

    .line 1117
    .line 1118
    :goto_29
    move v3, v4

    .line 1119
    goto :goto_2a

    .line 1120
    :pswitch_2
    sget-object v1, Lcom/vkontakte/android/attachments/DisclaimerType;->BANKRUPTCY:Lcom/vkontakte/android/attachments/DisclaimerType;

    .line 1121
    .line 1122
    goto :goto_29

    .line 1123
    :pswitch_3
    sget-object v1, Lcom/vkontakte/android/attachments/DisclaimerType;->CREDITS:Lcom/vkontakte/android/attachments/DisclaimerType;

    .line 1124
    .line 1125
    goto :goto_2a

    .line 1126
    :pswitch_4
    sget-object v1, Lcom/vkontakte/android/attachments/DisclaimerType;->SUPPLEMENT:Lcom/vkontakte/android/attachments/DisclaimerType;

    .line 1127
    .line 1128
    goto :goto_2a

    .line 1129
    :pswitch_5
    sget-object v1, Lcom/vkontakte/android/attachments/DisclaimerType;->DRUG:Lcom/vkontakte/android/attachments/DisclaimerType;

    .line 1130
    .line 1131
    const/16 v6, 0x28

    .line 1132
    .line 1133
    const v3, 0x3d4ccccd    # 0.05f

    .line 1134
    .line 1135
    .line 1136
    :goto_2a
    if-eqz v1, :cond_2c

    .line 1137
    .line 1138
    new-instance v4, Lcom/vkontakte/android/attachments/DisclaimerData;

    .line 1139
    .line 1140
    invoke-direct {v4, v1, v3, v6}, Lcom/vkontakte/android/attachments/DisclaimerData;-><init>(Lcom/vkontakte/android/attachments/DisclaimerType;FI)V

    .line 1141
    .line 1142
    .line 1143
    move-object v13, v4

    .line 1144
    goto :goto_2b

    .line 1145
    :cond_2c
    const/4 v13, 0x0

    .line 1146
    :goto_2b
    if-eqz v0, :cond_2d

    .line 1147
    .line 1148
    iget-object v1, v0, Lmo/b5;->d:Lmo/ec;

    .line 1149
    .line 1150
    if-eqz v1, :cond_2d

    .line 1151
    .line 1152
    invoke-virtual {v1}, Lmo/ec;->a()Lqo/a;

    .line 1153
    .line 1154
    .line 1155
    move-result-object v1

    .line 1156
    if-eqz v1, :cond_2d

    .line 1157
    .line 1158
    iget-object v1, v1, Lqo/a;->c:Ljava/lang/String;

    .line 1159
    .line 1160
    move-object/from16 v25, v1

    .line 1161
    .line 1162
    goto :goto_2c

    .line 1163
    :cond_2d
    const/16 v25, 0x0

    .line 1164
    .line 1165
    :goto_2c
    if-nez v23, :cond_2e

    .line 1166
    .line 1167
    invoke-static {}, Lsi2/a;->a()Lcom/vk/feed/core/models/header/EntryHeader;

    .line 1168
    .line 1169
    .line 1170
    move-result-object v23

    .line 1171
    :cond_2e
    move-object/from16 v15, v23

    .line 1172
    .line 1173
    new-instance v4, Lcom/vk/dto/newsfeed/entries/MyTargetNativeAdEntry;

    .line 1174
    .line 1175
    const v26, 0x61000

    .line 1176
    .line 1177
    .line 1178
    const/16 v27, 0x0

    .line 1179
    .line 1180
    const/16 v17, 0x0

    .line 1181
    .line 1182
    move-object/from16 v6, v20

    .line 1183
    .line 1184
    const/16 v20, 0x0

    .line 1185
    .line 1186
    move-object/from16 v7, v22

    .line 1187
    .line 1188
    const/16 v22, 0x0

    .line 1189
    .line 1190
    const/16 v23, 0x0

    .line 1191
    .line 1192
    move-object/from16 v24, v0

    .line 1193
    .line 1194
    move-object/from16 v5, v19

    .line 1195
    .line 1196
    move-object/from16 v8, v33

    .line 1197
    .line 1198
    move-object/from16 v9, v34

    .line 1199
    .line 1200
    move-object/from16 v10, v35

    .line 1201
    .line 1202
    move-object/from16 v12, v36

    .line 1203
    .line 1204
    const/16 v30, 0x0

    .line 1205
    .line 1206
    move-object/from16 v19, v2

    .line 1207
    .line 1208
    invoke-direct/range {v4 .. v27}, Lcom/vk/dto/newsfeed/entries/MyTargetNativeAdEntry;-><init>(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lcom/vkontakte/android/attachments/DisclaimerData;Lcom/vk/dto/newsfeed/entries/DisclaimerContent;Lcom/vk/feed/core/models/header/EntryHeader;Lcom/vkontakte/android/attachments/PhotoAttachment;Lcom/vkontakte/android/attachments/VideoAttachment;Lcom/vk/feed/core/models/MyTargetBrandLiftSurvey;Lx92/c;ZZLgp/b;Lmo2/a;Lcp/c;Ljava/lang/String;ILkotlin/jvm/internal/DefaultConstructorMarker;)V

    .line 1209
    .line 1210
    .line 1211
    move-object/from16 v5, v18

    .line 1212
    .line 1213
    if-eqz v5, :cond_30

    .line 1214
    .line 1215
    new-instance v14, Lcom/vk/dto/newsfeed/entries/MyTargetNativeAdEntry$b;

    .line 1216
    .line 1217
    iget-object v0, v5, Lcom/vk/feed/core/models/MyTargetBrandLiftSurvey;->a:Ljava/util/List;

    .line 1218
    .line 1219
    invoke-static {v0}, Lkotlin/collections/w;->d0(Ljava/util/List;)Ljava/lang/Object;

    .line 1220
    .line 1221
    .line 1222
    move-result-object v0

    .line 1223
    check-cast v0, Lcom/vk/feed/core/models/MyTargetBrandLiftSurvey$Question;

    .line 1224
    .line 1225
    if-eqz v0, :cond_2f

    .line 1226
    .line 1227
    iget-object v0, v0, Lcom/vk/feed/core/models/MyTargetBrandLiftSurvey$Question;->a:Ljava/lang/String;

    .line 1228
    .line 1229
    goto :goto_2d

    .line 1230
    :cond_2f
    const-string v0, ""

    .line 1231
    .line 1232
    :goto_2d
    new-instance v1, Ljava/util/LinkedHashMap;

    .line 1233
    .line 1234
    invoke-direct {v1}, Ljava/util/LinkedHashMap;-><init>()V

    .line 1235
    .line 1236
    .line 1237
    const/4 v6, 0x0

    .line 1238
    invoke-direct {v14, v0, v1, v6}, Lcom/vk/dto/newsfeed/entries/MyTargetNativeAdEntry$b;-><init>(Ljava/lang/String;Ljava/util/Map;Z)V

    .line 1239
    .line 1240
    .line 1241
    goto :goto_2e

    .line 1242
    :cond_30
    move-object/from16 v14, v30

    .line 1243
    .line 1244
    :goto_2e
    iput-object v14, v4, Lcom/vk/dto/newsfeed/entries/MyTargetNativeAdEntry;->D:Lcom/vk/dto/newsfeed/entries/MyTargetNativeAdEntry$b;

    .line 1245
    .line 1246
    return-object v4

    .line 1247
    :pswitch_data_0
    .packed-switch 0x6
        :pswitch_5
        :pswitch_0
        :pswitch_5
        :pswitch_4
        :pswitch_0
        :pswitch_3
        :pswitch_2
        :pswitch_1
    .end packed-switch
.end method
```

---

## 3. PromoPost Feed Entry Parser

### Descriptor
`Lcom/vk/dto/newsfeed/entries/PromoPost$a;->a(Lorg/json/JSONObject;Landroid/util/ArrayMap;Landroid/util/SparseArray;Ljava/util/Map;)Lcom/vk/dto/newsfeed/entries/PromoPost;`

### First 30 Instructions
```smali
.method public static a(Lorg/json/JSONObject;Landroid/util/ArrayMap;Landroid/util/SparseArray;Ljava/util/Map;)Lcom/vk/dto/newsfeed/entries/PromoPost;
    .locals 34

    .line 1
    move-object/from16 v0, p0

    .line 2
    .line 3
    move-object/from16 v1, p3

    .line 4
    .line 5
    const-string v2, "ads"

    .line 6
    .line 7
    invoke-virtual {v0, v2}, Lorg/json/JSONObject;->getJSONArray(Ljava/lang/String;)Lorg/json/JSONArray;

    .line 8
    .line 9
    .line 10
    move-result-object v2

    .line 11
    invoke-virtual {v2}, Lorg/json/JSONArray;->length()I

    .line 12
    .line 13
    .line 14
    move-result v3
```

### String Constants
```text
['', 'action', 'ad_choices', 'ad_data', 'ad_data_impression', 'ad_marker', 'ad_source', 'ad_url', 'ads', 'ads_debug', 'ads_id1', 'ads_id2', 'ads_statistics', 'ads_title', 'advertiser', 'advertiser_info_url', 'age_restriction', 'allow_hide', 'background_color', 'banner', 'banner_id', 'buttons', 'description', 'disclaimer', 'disclaimer_img', 'feedback', 'has_cta_button', 'header', 'header_catch_up_link', 'hide_reasons', 'icon', 'id', 'image_ratio', 'impression', 'items', 'min_height', 'name', 'post', 'post_ads_info', 'recommendation_info', 'stars', 'statistics', 'style', 'time_to_live', 'title', 'track_code', 'ttl', 'type']
```

### Opcodes Used
```text
['add-int/2addr', 'add-int/lit8', 'aget-object', 'array-length', 'check-cast', 'const', 'const-class', 'const-string', 'const/16', 'const/4', 'goto', 'goto/16', 'if-eq', 'if-eqz', 'if-ge', 'if-gt', 'if-ne', 'if-nez', 'iget-boolean', 'iget-object', 'instance-of', 'invoke-direct', 'invoke-direct/range', 'invoke-interface', 'invoke-interface/range', 'invoke-static', 'invoke-virtual', 'invoke-virtual/range', 'iput-object', 'move', 'move-object', 'move-object/from16', 'move-result', 'move-result-object', 'move/from16', 'new-instance', 'return-object', 'sget-object']
```

### Full Un-elided Smali Body
```smali
.method public static a(Lorg/json/JSONObject;Landroid/util/ArrayMap;Landroid/util/SparseArray;Ljava/util/Map;)Lcom/vk/dto/newsfeed/entries/PromoPost;
    .locals 34

    .line 1
    move-object/from16 v0, p0

    .line 2
    .line 3
    move-object/from16 v1, p3

    .line 4
    .line 5
    const-string v2, "ads"

    .line 6
    .line 7
    invoke-virtual {v0, v2}, Lorg/json/JSONObject;->getJSONArray(Ljava/lang/String;)Lorg/json/JSONArray;

    .line 8
    .line 9
    .line 10
    move-result-object v2

    .line 11
    invoke-virtual {v2}, Lorg/json/JSONArray;->length()I

    .line 12
    .line 13
    .line 14
    move-result v3

    .line 15
    if-nez v3, :cond_0

    .line 16
    .line 17
    :goto_0
    const/16 v16, 0x0

    .line 18
    .line 19
    goto/16 :goto_5

    .line 20
    .line 21
    :cond_0
    const-string v3, "ads_id1"

    .line 22
    .line 23
    invoke-virtual {v0, v3}, Lorg/json/JSONObject;->getInt(Ljava/lang/String;)I

    .line 24
    .line 25
    .line 26
    move-result v6

    .line 27
    const-string v3, "ads_id2"

    .line 28
    .line 29
    invoke-virtual {v0, v3}, Lorg/json/JSONObject;->getInt(Ljava/lang/String;)I

    .line 30
    .line 31
    .line 32
    move-result v7

    .line 33
    const-string v3, "ads_title"

    .line 34
    .line 35
    invoke-virtual {v0, v3}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    .line 36
    .line 37
    .line 38
    move-result-object v8

    .line 39
    const-string v3, "ads_debug"

    .line 40
    .line 41
    invoke-virtual {v0, v3}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    .line 42
    .line 43
    .line 44
    move-result-object v15

    .line 45
    const/4 v3, 0x0

    .line 46
    invoke-virtual {v2, v3}, Lorg/json/JSONArray;->getJSONObject(I)Lorg/json/JSONObject;

    .line 47
    .line 48
    .line 49
    move-result-object v2

    .line 50
    const-string v5, "advertiser_info_url"

    .line 51
    .line 52
    invoke-virtual {v0, v5}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    .line 53
    .line 54
    .line 55
    move-result-object v20

    .line 56
    const-string v5, "ad_marker"

    .line 57
    .line 58
    invoke-virtual {v0, v5}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    .line 59
    .line 60
    .line 61
    move-result-object v21

    .line 62
    const-string v5, "time_to_live"

    .line 63
    .line 64
    invoke-virtual {v2, v5}, Lorg/json/JSONObject;->optInt(Ljava/lang/String;)I

    .line 65
    .line 66
    .line 67
    move-result v5

    .line 68
    if-eqz v5, :cond_2

    .line 69
    .line 70
    const v9, 0x278d00

    .line 71
    .line 72
    .line 73
    if-gt v5, v9, :cond_2

    .line 74
    .line 75
    invoke-static {}, Lz12/u1;->a()I

    .line 76
    .line 77
    .line 78
    move-result v9

    .line 79
    add-int/2addr v5, v9

    .line 80
    :cond_1
    :goto_1
    move v10, v5

    .line 81
    goto :goto_2

    .line 82
    :cond_2
    if-nez v5, :cond_1

    .line 83
    .line 84
    const v5, 0x7fffffff

    .line 85
    .line 86
    .line 87
    goto :goto_1

    .line 88
    :goto_2
    const-string v5, "ad_data"

    .line 89
    .line 90
    invoke-virtual {v2, v5}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    .line 91
    .line 92
    .line 93
    move-result-object v9

    .line 94
    const-string v5, "age_restriction"

    .line 95
    .line 96
    invoke-virtual {v2, v5}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    .line 97
    .line 98
    .line 99
    move-result-object v13

    .line 100
    const-string v5, "disclaimer"

    .line 101
    .line 102
    invoke-virtual {v2, v5}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    .line 103
    .line 104
    .line 105
    move-result-object v14

    .line 106
    const-string v5, "header"

    .line 107
    .line 108
    invoke-virtual {v0, v5}, Lorg/json/JSONObject;->optJSONObject(Ljava/lang/String;)Lorg/json/JSONObject;

    .line 109
    .line 110
    .line 111
    move-result-object v5

    .line 112
    if-eqz v5, :cond_3

    .line 113
    .line 114
    invoke-static {v5, v1}, Landroidx/compose/material/w1;->A(Lorg/json/JSONObject;Ljava/util/Map;)Lcom/vk/feed/core/models/header/EntryHeader;

    .line 115
    .line 116
    .line 117
    move-result-object v5

    .line 118
    move-object v12, v5

    .line 119
    goto :goto_3

    .line 120
    :cond_3
    const/4 v12, 0x0

    .line 121
    :goto_3
    const-string v5, "post"

    .line 122
    .line 123
    invoke-virtual {v2, v5}, Lorg/json/JSONObject;->optJSONObject(Ljava/lang/String;)Lorg/json/JSONObject;

    .line 124
    .line 125
    .line 126
    move-result-object v5

    .line 127
    if-nez v5, :cond_4

    .line 128
    .line 129
    goto :goto_0

    .line 130
    :cond_4
    sget-object v11, Lrq1/d$c;->a:Lrq1/d$c;

    .line 131
    .line 132
    move-object/from16 v3, p1

    .line 133
    .line 134
    move-object/from16 v4, p2

    .line 135
    .line 136
    const/16 v16, 0x0

    .line 137
    .line 138
    invoke-static {v5, v3, v4, v1, v11}, Lmb2/h;->a(Lorg/json/JSONObject;Landroid/util/ArrayMap;Landroid/util/SparseArray;Ljava/util/Map;Lrq1/d;)Lcom/vk/dto/newsfeed/entries/Post;

    .line 139
    .line 140
    .line 141
    move-result-object v11

    .line 142
    if-eqz v11, :cond_7

    .line 143
    .line 144
    const-string v1, "feedback"

    .line 145
    .line 146
    invoke-virtual {v5, v1}, Lorg/json/JSONObject;->optJSONObject(Ljava/lang/String;)Lorg/json/JSONObject;

    .line 147
    .line 148
    .line 149
    move-result-object v1

    .line 150
    if-eqz v1, :cond_6

    .line 151
    .line 152
    const-string v3, "type"

    .line 153
    .line 154
    invoke-virtual {v1, v3}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    .line 155
    .line 156
    .line 157
    move-result-object v3

    .line 158
    const-string v4, "stars"

    .line 159
    .line 160
    invoke-static {v3, v4}, Lkotlin/jvm/internal/h;->e(Ljava/lang/Object;Ljava/lang/Object;)Z

    .line 161
    .line 162
    .line 163
    move-result v4

    .line 164
    if-eqz v4, :cond_5

    .line 165
    .line 166
    sget-object v3, Lcom/vk/feed/core/models/feedback/StarsFeedback;->CREATOR:Lcom/vk/core/serialize/Serializer$c;

    .line 167
    .line 168
    invoke-static {v1}, Lcom/vk/feed/core/models/feedback/StarsFeedback$a;->a(Lorg/json/JSONObject;)Lcom/vk/feed/core/models/feedback/StarsFeedback;

    .line 169
    .line 170
    .line 171
    move-result-object v1

    .line 172
    goto :goto_4

    .line 173
    :cond_5
    const-string v4, "buttons"

    .line 174
    .line 175
    invoke-static {v3, v4}, Lkotlin/jvm/internal/h;->e(Ljava/lang/Object;Ljava/lang/Object;)Z

    .line 176
    .line 177
    .line 178
    move-result v3

    .line 179
    if-eqz v3, :cond_6

    .line 180
    .line 181
    sget-object v3, Lcom/vk/feed/core/models/feedback/ButtonsFeedback;->CREATOR:Lcom/vk/core/serialize/Serializer$c;

    .line 182
    .line 183
    invoke-static {v1}, Lcom/vk/feed/core/models/feedback/ButtonsFeedback$a;->a(Lorg/json/JSONObject;)Lcom/vk/feed/core/models/feedback/ButtonsFeedback;

    .line 184
    .line 185
    .line 186
    move-result-object v1

    .line 187
    goto :goto_4

    .line 188
    :cond_6
    move-object/from16 v1, v16

    .line 189
    .line 190
    :goto_4
    iput-object v1, v11, Lcom/vk/feed/core/models/news/NewsEntry;->f:Lcom/vk/feed/core/models/feedback/Feedback;

    .line 191
    .line 192
    :cond_7
    if-nez v11, :cond_8

    .line 193
    .line 194
    :goto_5
    return-object v16

    .line 195
    :cond_8
    iget-object v1, v11, Lcom/vk/dto/newsfeed/entries/Post;->y:Ljava/util/ArrayList;

    .line 196
    .line 197
    const-string v3, "header_catch_up_link"

    .line 198
    .line 199
    invoke-virtual {v2, v3}, Lorg/json/JSONObject;->optJSONObject(Ljava/lang/String;)Lorg/json/JSONObject;

    .line 200
    .line 201
    .line 202
    move-result-object v3

    .line 203
    const-string v4, "track_code"

    .line 204
    .line 205
    const-string v5, "icon"

    .line 206
    .line 207
    move-object/from16 p1, v1

    .line 208
    .line 209
    if-eqz v3, :cond_c

    .line 210
    .line 211
    sget-object v17, Lcom/vk/dto/profile/CatchUpBanner;->CREATOR:Lcom/vk/core/serialize/Serializer$c;

    .line 212
    .line 213
    const-string v1, ""

    .line 214
    .line 215
    move/from16 v17, v6

    .line 216
    .line 217
    :try_start_0
    const-string v6, "banner"

    .line 218
    .line 219
    invoke-virtual {v3, v6}, Lorg/json/JSONObject;->optJSONObject(Ljava/lang/String;)Lorg/json/JSONObject;

    .line 220
    .line 221
    .line 222
    move-result-object v6

    .line 223
    if-nez v6, :cond_9

    .line 224
    .line 225
    goto :goto_6

    .line 226
    :cond_9
    move-object v3, v6

    .line 227
    :goto_6
    new-instance v22, Lcom/vk/dto/profile/CatchUpBanner;

    .line 228
    .line 229
    const-string v6, "banner_id"

    .line 230
    .line 231
    invoke-virtual {v3, v6}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    .line 232
    .line 233
    .line 234
    move-result-object v23

    .line 235
    new-instance v6, Lcom/vk/dto/newsfeed/ButtonAction;
    :try_end_0
    .catch Ljava/lang/Exception; {:try_start_0 .. :try_end_0} :catch_0

    .line 236
    .line 237
    move/from16 v18, v7

    .line 238
    .line 239
    :try_start_1
    const-string v7, "action"

    .line 240
    .line 241
    invoke-virtual {v3, v7}, Lorg/json/JSONObject;->optJSONObject(Ljava/lang/String;)Lorg/json/JSONObject;

    .line 242
    .line 243
    .line 244
    move-result-object v7

    .line 245
    invoke-direct {v6, v7}, Lcom/vk/dto/newsfeed/ButtonAction;-><init>(Lorg/json/JSONObject;)V

    .line 246
    .line 247
    .line 248
    const-string v7, "title"

    .line 249
    .line 250
    invoke-virtual {v3, v7, v1}, Lorg/json/JSONObject;->optString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    .line 251
    .line 252
    .line 253
    move-result-object v25

    .line 254
    const-string v7, "description"

    .line 255
    .line 256
    invoke-virtual {v3, v7, v1}, Lorg/json/JSONObject;->optString(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;

    .line 257
    .line 258
    .line 259
    move-result-object v26

    .line 260
    const-string v1, "ttl"

    .line 261
    .line 262
    const/16 v7, 0xa

    .line 263
    .line 264
    invoke-virtual {v3, v1, v7}, Lorg/json/JSONObject;->optInt(Ljava/lang/String;I)I

    .line 265
    .line 266
    .line 267
    move-result v27

    .line 268
    new-instance v1, Lcom/vk/dto/common/Image;

    .line 269
    .line 270
    invoke-virtual {v3, v5}, Lorg/json/JSONObject;->optJSONArray(Ljava/lang/String;)Lorg/json/JSONArray;

    .line 271
    .line 272
    .line 273
    move-result-object v7
    :try_end_1
    .catch Ljava/lang/Exception; {:try_start_1 .. :try_end_1} :catch_1

    .line 274
    move-object/from16 v24, v6

    .line 275
    .line 276
    const/4 v6, 0x2

    .line 277
    move-object/from16 v19, v8

    .line 278
    .line 279
    move-object/from16 v8, v16

    .line 280
    .line 281
    :try_start_2
    invoke-direct {v1, v7, v8, v6, v8}, Lcom/vk/dto/common/Image;-><init>(Lorg/json/JSONArray;Ljava/lang/String;ILkotlin/jvm/internal/DefaultConstructorMarker;)V

    .line 282
    .line 283
    .line 284
    const-string v6, "allow_hide"

    .line 285
    .line 286
    const/4 v7, 0x1

    .line 287
    invoke-virtual {v3, v6, v7}, Lorg/json/JSONObject;->optBoolean(Ljava/lang/String;Z)Z

    .line 288
    .line 289
    .line 290
    move-result v29

    .line 291
    const-string v6, "background_color"

    .line 292
    .line 293
    invoke-virtual {v3, v6}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    .line 294
    .line 295
    .line 296
    move-result-object v30

    .line 297
    invoke-virtual {v3, v4}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    .line 298
    .line 299
    .line 300
    move-result-object v31

    .line 301
    const-string v6, "advertiser"

    .line 302
    .line 303
    invoke-virtual {v3, v6}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    .line 304
    .line 305
    .line 306
    move-result-object v32

    .line 307
    sget-object v6, Lcom/vk/dto/profile/CatchUpBanner$Style;->Companion:Lcom/vk/dto/profile/CatchUpBanner$Style$a;

    .line 308
    .line 309
    const-string v7, "style"

    .line 310
    .line 311
    invoke-virtual {v3, v7}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    .line 312
    .line 313
    .line 314
    move-result-object v3

    .line 315
    invoke-virtual {v6}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    .line 316
    .line 317
    .line 318
    invoke-static {}, Lcom/vk/dto/profile/CatchUpBanner$Style;->values()[Lcom/vk/dto/profile/CatchUpBanner$Style;

    .line 319
    .line 320
    .line 321
    move-result-object v6

    .line 322
    array-length v7, v6

    .line 323
    const/4 v8, 0x0

    .line 324
    :goto_7
    if-ge v8, v7, :cond_b

    .line 325
    .line 326
    aget-object v28, v6, v8

    .line 327
    .line 328
    move-object/from16 p3, v1

    .line 329
    .line 330
    invoke-virtual/range {v28 .. v28}, Lcom/vk/dto/profile/CatchUpBanner$Style;->a()Ljava/lang/String;

    .line 331
    .line 332
    .line 333
    move-result-object v1

    .line 334
    invoke-static {v1, v3}, Lkotlin/jvm/internal/h;->e(Ljava/lang/Object;Ljava/lang/Object;)Z

    .line 335
    .line 336
    .line 337
    move-result v1

    .line 338
    if-eqz v1, :cond_a

    .line 339
    .line 340
    :goto_8
    move-object/from16 v33, v28

    .line 341
    .line 342
    move-object/from16 v28, p3

    .line 343
    .line 344
    goto :goto_9

    .line 345
    :cond_a
    add-int/lit8 v8, v8, 0x1

    .line 346
    .line 347
    move-object/from16 v1, p3

    .line 348
    .line 349
    goto :goto_7

    .line 350
    :cond_b
    move-object/from16 p3, v1

    .line 351
    .line 352
    sget-object v28, Lcom/vk/dto/profile/CatchUpBanner$Style;->NONE:Lcom/vk/dto/profile/CatchUpBanner$Style;

    .line 353
    .line 354
    goto :goto_8

    .line 355
    :goto_9
    invoke-direct/range {v22 .. v33}, Lcom/vk/dto/profile/CatchUpBanner;-><init>(Ljava/lang/String;Lcom/vk/dto/newsfeed/ButtonAction;Ljava/lang/String;Ljava/lang/String;ILcom/vk/dto/common/Image;ZLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Lcom/vk/dto/profile/CatchUpBanner$Style;)V
    :try_end_2
    .catch Ljava/lang/Exception; {:try_start_2 .. :try_end_2} :catch_2

    .line 356
    .line 357
    .line 358
    goto :goto_b

    .line 359
    :catch_0
    move/from16 v18, v7

    .line 360
    .line 361
    goto :goto_a

    .line 362
    :cond_c
    move/from16 v17, v6

    .line 363
    .line 364
    move/from16 v18, v7

    .line 365
    .line 366
    :catch_1
    :goto_a
    move-object/from16 v19, v8

    .line 367
    .line 368
    :catch_2
    const/16 v22, 0x0

    .line 369
    .line 370
    :goto_b
    const-string v1, "recommendation_info"

    .line 371
    .line 372
    invoke-virtual {v0, v1}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    .line 373
    .line 374
    .line 375
    move-result-object v26

    .line 376
    const-string v1, "ad_url"

    .line 377
    .line 378
    invoke-virtual {v0, v1}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    .line 379
    .line 380
    .line 381
    move-result-object v27

    .line 382
    sget-object v1, Lcom/vkontakte/android/attachments/AdSource;->Companion:Lcom/vkontakte/android/attachments/AdSource$a;

    .line 383
    .line 384
    const-string v3, "ad_source"

    .line 385
    .line 386
    invoke-virtual {v0, v3}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    .line 387
    .line 388
    .line 389
    move-result-object v3

    .line 390
    invoke-virtual {v1}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    .line 391
    .line 392
    .line 393
    invoke-static {v3}, Lcom/vkontakte/android/attachments/AdSource$a;->a(Ljava/lang/String;)Lcom/vkontakte/android/attachments/AdSource;

    .line 394
    .line 395
    .line 396
    move-result-object v23

    .line 397
    const-string v1, "hide_reasons"

    .line 398
    .line 399
    invoke-virtual {v0, v1}, Lorg/json/JSONObject;->optJSONObject(Ljava/lang/String;)Lorg/json/JSONObject;

    .line 400
    .line 401
    .line 402
    move-result-object v1

    .line 403
    const-string v3, "id"

    .line 404
    .line 405
    if-eqz v1, :cond_12

    .line 406
    .line 407
    const-string v6, "items"

    .line 408
    .line 409
    invoke-virtual {v1, v6}, Lorg/json/JSONObject;->optJSONArray(Ljava/lang/String;)Lorg/json/JSONArray;

    .line 410
    .line 411
    .line 412
    move-result-object v1

    .line 413
    if-eqz v1, :cond_f

    .line 414
    .line 415
    new-instance v6, Ljava/util/ArrayList;

    .line 416
    .line 417
    invoke-virtual {v1}, Lorg/json/JSONArray;->length()I

    .line 418
    .line 419
    .line 420
    move-result v7

    .line 421
    invoke-direct {v6, v7}, Ljava/util/ArrayList;-><init>(I)V

    .line 422
    .line 423
    .line 424
    invoke-virtual {v1}, Lorg/json/JSONArray;->length()I

    .line 425
    .line 426
    .line 427
    move-result v7

    .line 428
    const/4 v8, 0x0

    .line 429
    :goto_c
    if-ge v8, v7, :cond_e

    .line 430
    .line 431
    move/from16 p3, v7

    .line 432
    .line 433
    invoke-virtual {v1, v8}, Lorg/json/JSONArray;->optJSONObject(I)Lorg/json/JSONObject;

    .line 434
    .line 435
    .line 436
    move-result-object v7

    .line 437
    move-object/from16 v24, v1

    .line 438
    .line 439
    if-eqz v7, :cond_d

    .line 440
    .line 441
    new-instance v1, Lcom/vkontakte/android/attachments/AdHideReason;

    .line 442
    .line 443
    move/from16 v25, v8

    .line 444
    .line 445
    invoke-virtual {v7, v3}, Lorg/json/JSONObject;->getInt(Ljava/lang/String;)I

    .line 446
    .line 447
    .line 448
    move-result v8

    .line 449
    move-object/from16 v28, v9

    .line 450
    .line 451
    const-string v9, "name"

    .line 452
    .line 453
    invoke-virtual {v7, v9}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    .line 454
    .line 455
    .line 456
    move-result-object v9

    .line 457
    invoke-virtual {v7, v5}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    .line 458
    .line 459
    .line 460
    move-result-object v7

    .line 461
    invoke-direct {v1, v8, v9, v7}, Lcom/vkontakte/android/attachments/AdHideReason;-><init>(ILjava/lang/String;Ljava/lang/String;)V

    .line 462
    .line 463
    .line 464
    invoke-virtual {v6, v1}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    .line 465
    .line 466
    .line 467
    goto :goto_d

    .line 468
    :cond_d
    move/from16 v25, v8

    .line 469
    .line 470
    move-object/from16 v28, v9

    .line 471
    .line 472
    :goto_d
    add-int/lit8 v8, v25, 0x1

    .line 473
    .line 474
    move/from16 v7, p3

    .line 475
    .line 476
    move-object/from16 v1, v24

    .line 477
    .line 478
    move-object/from16 v9, v28

    .line 479
    .line 480
    goto :goto_c

    .line 481
    :cond_e
    :goto_e
    move-object/from16 v28, v9

    .line 482
    .line 483
    goto :goto_f

    .line 484
    :cond_f
    const/4 v6, 0x0

    .line 485
    goto :goto_e

    .line 486
    :goto_f
    if-eqz v6, :cond_13

    .line 487
    .line 488
    new-instance v1, Ljava/util/ArrayList;

    .line 489
    .line 490
    invoke-direct {v1}, Ljava/util/ArrayList;-><init>()V

    .line 491
    .line 492
    .line 493
    invoke-interface {v6}, Ljava/lang/Iterable;->iterator()Ljava/util/Iterator;

    .line 494
    .line 495
    .line 496
    move-result-object v5

    .line 497
    :cond_10
    :goto_10
    invoke-interface {v5}, Ljava/util/Iterator;->hasNext()Z

    .line 498
    .line 499
    .line 500
    move-result v6

    .line 501
    if-eqz v6, :cond_14

    .line 502
    .line 503
    invoke-interface {v5}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    .line 504
    .line 505
    .line 506
    move-result-object v6

    .line 507
    move-object v7, v6

    .line 508
    check-cast v7, Lcom/vkontakte/android/attachments/AdHideReason;

    .line 509
    .line 510
    iget-object v7, v7, Lcom/vkontakte/android/attachments/AdHideReason;->b:Ljava/lang/String;

    .line 511
    .line 512
    if-eqz v7, :cond_10

    .line 513
    .line 514
    invoke-virtual {v7}, Ljava/lang/String;->length()I

    .line 515
    .line 516
    .line 517
    move-result v7

    .line 518
    if-nez v7, :cond_11

    .line 519
    .line 520
    goto :goto_10

    .line 521
    :cond_11
    invoke-virtual {v1, v6}, Ljava/util/ArrayList;->add(Ljava/lang/Object;)Z

    .line 522
    .line 523
    .line 524
    goto :goto_10

    .line 525
    :cond_12
    move-object/from16 v28, v9

    .line 526
    .line 527
    :cond_13
    const/4 v1, 0x0

    .line 528
    :cond_14
    const-string v5, "disclaimer_img"

    .line 529
    .line 530
    invoke-virtual {v2, v5}, Lorg/json/JSONObject;->optJSONObject(Ljava/lang/String;)Lorg/json/JSONObject;

    .line 531
    .line 532
    .line 533
    move-result-object v5

    .line 534
    if-eqz v5, :cond_18

    .line 535
    .line 536
    invoke-virtual {v5, v3}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    .line 537
    .line 538
    .line 539
    move-result-object v3

    .line 540
    if-nez v3, :cond_15

    .line 541
    .line 542
    :goto_11
    const/4 v7, 0x0

    .line 543
    goto :goto_14

    .line 544
    :cond_15
    :try_start_3
    const-class v6, Lcom/vkontakte/android/attachments/DisclaimerType;

    .line 545
    .line 546
    sget-object v7, Ljava/util/Locale;->US:Ljava/util/Locale;

    .line 547
    .line 548
    invoke-virtual {v3, v7}, Ljava/lang/String;->toUpperCase(Ljava/util/Locale;)Ljava/lang/String;

    .line 549
    .line 550
    .line 551
    move-result-object v3

    .line 552
    invoke-static {v6, v3}, Ljava/lang/Enum;->valueOf(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/Enum;

    .line 553
    .line 554
    .line 555
    move-result-object v3
    :try_end_3
    .catch Ljava/lang/IllegalArgumentException; {:try_start_3 .. :try_end_3} :catch_3

    .line 556
    goto :goto_12

    .line 557
    :catch_3
    const/4 v3, 0x0

    .line 558
    :goto_12
    check-cast v3, Lcom/vkontakte/android/attachments/DisclaimerType;

    .line 559
    .line 560
    if-nez v3, :cond_16

    .line 561
    .line 562
    goto :goto_11

    .line 563
    :cond_16
    const-string v6, "image_ratio"

    .line 564
    .line 565
    invoke-static {v6, v5}, Lcx1/s0;->d(Ljava/lang/String;Lorg/json/JSONObject;)Ljava/lang/Float;

    .line 566
    .line 567
    .line 568
    move-result-object v6

    .line 569
    if-eqz v6, :cond_17

    .line 570
    .line 571
    invoke-virtual {v6}, Ljava/lang/Float;->floatValue()F

    .line 572
    .line 573
    .line 574
    move-result v6

    .line 575
    goto :goto_13

    .line 576
    :cond_17
    const v6, 0x3dcccccd    # 0.1f

    .line 577
    .line 578
    .line 579
    :goto_13
    const-string v7, "min_height"

    .line 580
    .line 581
    invoke-virtual {v5, v7}, Lorg/json/JSONObject;->optInt(Ljava/lang/String;)I

    .line 582
    .line 583
    .line 584
    move-result v5

    .line 585
    new-instance v7, Lcom/vkontakte/android/attachments/DisclaimerData;

    .line 586
    .line 587
    invoke-direct {v7, v3, v6, v5}, Lcom/vkontakte/android/attachments/DisclaimerData;-><init>(Lcom/vkontakte/android/attachments/DisclaimerType;FI)V

    .line 588
    .line 589
    .line 590
    :goto_14
    move-object/from16 v24, v7

    .line 591
    .line 592
    goto :goto_15

    .line 593
    :cond_18
    const/16 v24, 0x0

    .line 594
    .line 595
    :goto_15
    if-eqz p1, :cond_1a

    .line 596
    .line 597
    invoke-interface/range {p1 .. p1}, Ljava/util/Collection;->isEmpty()Z

    .line 598
    .line 599
    .line 600
    move-result v3

    .line 601
    if-eqz v3, :cond_1a

    .line 602
    .line 603
    :cond_19
    const/4 v6, 0x1

    .line 604
    goto :goto_17

    .line 605
    :cond_1a
    invoke-interface/range {p1 .. p1}, Ljava/lang/Iterable;->iterator()Ljava/util/Iterator;

    .line 606
    .line 607
    .line 608
    move-result-object v3

    .line 609
    :cond_1b
    invoke-interface {v3}, Ljava/util/Iterator;->hasNext()Z

    .line 610
    .line 611
    .line 612
    move-result v5

    .line 613
    if-eqz v5, :cond_19

    .line 614
    .line 615
    invoke-interface {v3}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    .line 616
    .line 617
    .line 618
    move-result-object v5

    .line 619
    check-cast v5, Lcom/vk/feed/core/models/attachment/EntryAttachment;

    .line 620
    .line 621
    iget-object v5, v5, Lcom/vk/feed/core/models/attachment/EntryAttachment;->a:Lcom/vk/dto/common/Attachment;

    .line 622
    .line 623
    invoke-virtual {v5}, Lcom/vk/dto/common/Attachment;->l8()I

    .line 624
    .line 625
    .line 626
    move-result v5

    .line 627
    const/4 v6, 0x1

    .line 628
    if-eq v5, v6, :cond_1c

    .line 629
    .line 630
    if-eqz v5, :cond_1c

    .line 631
    .line 632
    const/4 v7, 0x6

    .line 633
    if-ne v5, v7, :cond_1b

    .line 634
    .line 635
    :cond_1c
    sget-object v3, Lcom/vk/dto/newsfeed/entries/DisclaimerContent;->TEXT_WITH_CONTENT:Lcom/vk/dto/newsfeed/entries/DisclaimerContent;

    .line 636
    .line 637
    :goto_16
    move-object/from16 v29, v3

    .line 638
    .line 639
    goto :goto_19

    .line 640
    :goto_17
    if-eqz p1, :cond_1d

    .line 641
    .line 642
    invoke-interface/range {p1 .. p1}, Ljava/util/Collection;->isEmpty()Z

    .line 643
    .line 644
    .line 645
    move-result v3

    .line 646
    if-eqz v3, :cond_1d

    .line 647
    .line 648
    goto :goto_18

    .line 649
    :cond_1d
    invoke-interface/range {p1 .. p1}, Ljava/lang/Iterable;->iterator()Ljava/util/Iterator;

    .line 650
    .line 651
    .line 652
    move-result-object v3

    .line 653
    :cond_1e
    invoke-interface {v3}, Ljava/util/Iterator;->hasNext()Z

    .line 654
    .line 655
    .line 656
    move-result v5

    .line 657
    if-eqz v5, :cond_1f

    .line 658
    .line 659
    invoke-interface {v3}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    .line 660
    .line 661
    .line 662
    move-result-object v5

    .line 663
    check-cast v5, Lcom/vk/feed/core/models/attachment/EntryAttachment;

    .line 664
    .line 665
    iget-object v5, v5, Lcom/vk/feed/core/models/attachment/EntryAttachment;->a:Lcom/vk/dto/common/Attachment;

    .line 666
    .line 667
    instance-of v5, v5, Lcom/vkontakte/android/attachments/PrettyCardAttachment;

    .line 668
    .line 669
    if-eqz v5, :cond_1e

    .line 670
    .line 671
    sget-object v3, Lcom/vk/dto/newsfeed/entries/DisclaimerContent;->CARDS:Lcom/vk/dto/newsfeed/entries/DisclaimerContent;

    .line 672
    .line 673
    goto :goto_16

    .line 674
    :cond_1f
    :goto_18
    sget-object v3, Lcom/vk/dto/newsfeed/entries/DisclaimerContent;->TEXT:Lcom/vk/dto/newsfeed/entries/DisclaimerContent;

    .line 675
    .line 676
    goto :goto_16

    .line 677
    :goto_19
    const-string v3, "ad_choices"

    .line 678
    .line 679
    invoke-virtual {v0, v3}, Lorg/json/JSONObject;->optJSONObject(Ljava/lang/String;)Lorg/json/JSONObject;

    .line 680
    .line 681
    .line 682
    move-result-object v3

    .line 683
    if-eqz v3, :cond_20

    .line 684
    .line 685
    sget-object v5, Lcom/vk/dto/common/AdsChoices;->f:Lcom/vk/dto/common/AdsChoices$a;

    .line 686
    .line 687
    invoke-virtual {v5, v3}, Lcom/vk/dto/common/AdsChoices$a;->a(Lorg/json/JSONObject;)Ljava/lang/Object;

    .line 688
    .line 689
    .line 690
    move-result-object v3

    .line 691
    check-cast v3, Lcom/vk/dto/common/AdsChoices;

    .line 692
    .line 693
    goto :goto_1a

    .line 694
    :cond_20
    const/4 v3, 0x0

    .line 695
    :goto_1a
    invoke-virtual {v2, v4}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    .line 696
    .line 697
    .line 698
    move-result-object v30

    .line 699
    const-string v4, "post_ads_info"

    .line 700
    .line 701
    invoke-virtual {v2, v4}, Lorg/json/JSONObject;->optJSONObject(Ljava/lang/String;)Lorg/json/JSONObject;

    .line 702
    .line 703
    .line 704
    move-result-object v4

    .line 705
    if-eqz v4, :cond_21

    .line 706
    .line 707
    new-instance v5, Lla2/n;

    .line 708
    .line 709
    const-string v7, "has_cta_button"

    .line 710
    .line 711
    invoke-virtual {v4, v7}, Lorg/json/JSONObject;->optBoolean(Ljava/lang/String;)Z

    .line 712
    .line 713
    .line 714
    move-result v4

    .line 715
    invoke-direct {v5, v4}, Lla2/n;-><init>(Z)V

    .line 716
    .line 717
    .line 718
    move-object/from16 v31, v5

    .line 719
    .line 720
    goto :goto_1b

    .line 721
    :cond_21
    const/16 v31, 0x0

    .line 722
    .line 723
    :goto_1b
    iget-object v4, v11, Lcom/vk/dto/newsfeed/entries/Post;->K:Lcom/vk/feed/core/models/news/NewsEntry$TrackData;

    .line 724
    .line 725
    if-eqz v1, :cond_22

    .line 726
    .line 727
    invoke-static {v1}, Lcx1/v;->o(Ljava/util/Collection;)Ljava/util/ArrayList;

    .line 728
    .line 729
    .line 730
    move-result-object v1

    .line 731
    move-object/from16 v16, v1

    .line 732
    .line 733
    goto :goto_1c

    .line 734
    :cond_22
    const/16 v16, 0x0

    .line 735
    .line 736
    :goto_1c
    new-instance v5, Lcom/vk/dto/newsfeed/entries/PromoPost;

    .line 737
    .line 738
    const v32, 0x82800

    .line 739
    .line 740
    .line 741
    const/16 v33, 0x0

    .line 742
    .line 743
    move/from16 v8, v17

    .line 744
    .line 745
    const/16 v17, 0x0

    .line 746
    .line 747
    move v7, v6

    .line 748
    move v6, v8

    .line 749
    move-object/from16 v8, v19

    .line 750
    .line 751
    const/16 v19, 0x0

    .line 752
    .line 753
    const/16 v25, 0x0

    .line 754
    .line 755
    move-object/from16 v1, v22

    .line 756
    .line 757
    move-object/from16 v22, v16

    .line 758
    .line 759
    move-object/from16 v16, v1

    .line 760
    .line 761
    move v1, v7

    .line 762
    move/from16 v7, v18

    .line 763
    .line 764
    move-object/from16 v9, v28

    .line 765
    .line 766
    move-object/from16 v28, v3

    .line 767
    .line 768
    move-object/from16 v18, v4

    .line 769
    .line 770
    invoke-direct/range {v5 .. v33}, Lcom/vk/dto/newsfeed/entries/PromoPost;-><init>(IILjava/lang/String;Ljava/lang/String;ILcom/vk/dto/newsfeed/entries/Post;Lcom/vk/feed/core/models/header/EntryHeader;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Lcom/vk/dto/profile/CatchUpBanner;Lcom/vk/statistic/DeprecatedStatisticUrl;Lcom/vk/feed/core/models/news/NewsEntry$TrackData;Lcom/vk/statistic/DeprecatedStatisticInterface$a;Ljava/lang/String;Ljava/lang/String;Ljava/util/ArrayList;Lcom/vkontakte/android/attachments/AdSource;Lcom/vkontakte/android/attachments/DisclaimerData;Lcom/vk/dto/newsfeed/entries/PixelStatsDelegate;Ljava/lang/String;Ljava/lang/String;Lcom/vk/dto/common/AdsChoices;Lcom/vk/dto/newsfeed/entries/DisclaimerContent;Ljava/lang/String;Lla2/n;ILkotlin/jvm/internal/DefaultConstructorMarker;)V

    .line 771
    .line 772
    .line 773
    const-string v3, "ads_statistics"

    .line 774
    .line 775
    invoke-virtual {v0, v3}, Lorg/json/JSONObject;->optJSONArray(Ljava/lang/String;)Lorg/json/JSONArray;

    .line 776
    .line 777
    .line 778
    move-result-object v0

    .line 779
    invoke-static {v0, v5, v5, v6, v7}, Lmx6/c;->c(Lorg/json/JSONArray;Lcom/vk/statistic/DeprecatedStatisticInterface;Lkb2/b;II)V

    .line 780
    .line 781
    .line 782
    const-string v0, "statistics"

    .line 783
    .line 784
    invoke-virtual {v2, v0}, Lorg/json/JSONObject;->optJSONArray(Ljava/lang/String;)Lorg/json/JSONArray;

    .line 785
    .line 786
    .line 787
    move-result-object v0

    .line 788
    invoke-static {v0, v5, v5, v6, v7}, Lmx6/c;->c(Lorg/json/JSONArray;Lcom/vk/statistic/DeprecatedStatisticInterface;Lkb2/b;II)V

    .line 789
    .line 790
    .line 791
    invoke-virtual/range {p1 .. p1}, Ljava/util/ArrayList;->iterator()Ljava/util/Iterator;

    .line 792
    .line 793
    .line 794
    move-result-object v0

    .line 795
    :cond_23
    :goto_1d
    invoke-interface {v0}, Ljava/util/Iterator;->hasNext()Z

    .line 796
    .line 797
    .line 798
    move-result v3

    .line 799
    if-eqz v3, :cond_26

    .line 800
    .line 801
    invoke-interface {v0}, Ljava/util/Iterator;->next()Ljava/lang/Object;

    .line 802
    .line 803
    .line 804
    move-result-object v3

    .line 805
    check-cast v3, Lcom/vk/feed/core/models/attachment/EntryAttachment;

    .line 806
    .line 807
    iget-object v3, v3, Lcom/vk/feed/core/models/attachment/EntryAttachment;->a:Lcom/vk/dto/common/Attachment;

    .line 808
    .line 809
    instance-of v4, v3, Lcom/vkontakte/android/attachments/VideoAttachment;

    .line 810
    .line 811
    if-eqz v4, :cond_23

    .line 812
    .line 813
    check-cast v3, Lcom/vkontakte/android/attachments/VideoAttachment;

    .line 814
    .line 815
    iget-object v3, v3, Lcom/vkontakte/android/attachments/VideoAttachment;->g:Ly33/a;

    .line 816
    .line 817
    if-eqz v3, :cond_23

    .line 818
    .line 819
    sget-object v4, Lcom/vkontakte/android/attachments/ShitAttachment;->CREATOR:Lcom/vk/core/serialize/Serializer$c;

    .line 820
    .line 821
    invoke-static {v5, v3}, Lcom/vkontakte/android/attachments/ShitAttachment$a;->a(Lcom/vk/statistic/DeprecatedStatisticInterface;Ly33/a;)V

    .line 822
    .line 823
    .line 824
    sget-object v4, Lcom/vk/toggle/d;->a:Lzx5/n0;

    .line 825
    .line 826
    sget-object v4, Lcom/vk/toggle/features/FeedFeatures;->FEED_PROMO_POST_VIDEO_AUTOPLAY_ADS_TAG:Lcom/vk/toggle/features/FeedFeatures;

    .line 827
    .line 828
    invoke-virtual {v4}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    .line 829
    .line 830
    .line 831
    sget-object v8, Lcom/vk/toggle/b;->A:Lcom/vk/toggle/b;

    .line 832
    .line 833
    invoke-virtual {v8, v4}, Lcom/vk/toggle/internal/ToggleManager;->c(Lcom/vk/toggle/b$a;)Z

    .line 834
    .line 835
    .line 836
    move-result v4

    .line 837
    if-eqz v4, :cond_23

    .line 838
    .line 839
    invoke-virtual {v11}, Lcom/vk/dto/newsfeed/entries/Post;->S8()Z

    .line 840
    .line 841
    .line 842
    move-result v4

    .line 843
    if-nez v4, :cond_25

    .line 844
    .line 845
    iget-boolean v4, v11, Lcom/vk/dto/newsfeed/entries/Post;->E:Z

    .line 846
    .line 847
    if-eqz v4, :cond_24

    .line 848
    .line 849
    goto :goto_1e

    .line 850
    :cond_24
    const/4 v4, 0x0

    .line 851
    goto :goto_1f

    .line 852
    :cond_25
    :goto_1e
    move v4, v1

    .line 853
    :goto_1f
    invoke-interface {v3, v4}, Ly33/a;->K(Z)V

    .line 854
    .line 855
    .line 856
    goto :goto_1d

    .line 857
    :cond_26
    const-string v0, "ad_data_impression"

    .line 858
    .line 859
    invoke-virtual {v2, v0}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    .line 860
    .line 861
    .line 862
    move-result-object v0

    .line 863
    sget-object v1, Lcom/vk/dto/ads/PixelStats;->CREATOR:Lcom/vk/core/serialize/Serializer$c;

    .line 864
    .line 865
    sget-object v1, Lcom/vk/api/generated/ads/dto/AdsItemBlockAdStatPixelDto$TypeDto;->IMPRESSION:Lcom/vk/api/generated/ads/dto/AdsItemBlockAdStatPixelDto$TypeDto;

    .line 866
    .line 867
    const/16 v2, 0x18

    .line 868
    .line 869
    const/4 v3, 0x0

    .line 870
    invoke-static {v1, v6, v7, v3, v2}, Lcom/vk/dto/ads/PixelStats$a;->a(Lcom/vk/api/generated/ads/dto/AdsItemBlockAdStatPixelDto$TypeDto;IIII)Ljava/lang/String;

    .line 871
    .line 872
    .line 873
    move-result-object v2

    .line 874
    new-instance v3, Lcom/vk/dto/ads/PixelStats;

    .line 875
    .line 876
    invoke-direct {v3, v0, v1, v2}, Lcom/vk/dto/ads/PixelStats;-><init>(Ljava/lang/String;Lcom/vk/api/generated/ads/dto/AdsItemBlockAdStatPixelDto$TypeDto;Ljava/lang/String;)V

    .line 877
    .line 878
    .line 879
    iget-object v1, v5, Lcom/vk/dto/newsfeed/entries/PromoPost;->A:Lcom/vk/dto/newsfeed/entries/PixelStatsDelegate;

    .line 880
    .line 881
    iput-object v3, v1, Lcom/vk/dto/newsfeed/entries/PixelStatsDelegate;->b:Lcom/vk/dto/ads/PixelStats;

    .line 882
    .line 883
    move-object v11, v5

    .line 884
    new-instance v5, Lcom/vk/statistic/DeprecatedStatisticUrl;

    .line 885
    .line 886
    move/from16 v18, v7

    .line 887
    .line 888
    const-string v7, "impression"

    .line 889
    .line 890
    const/4 v10, -0x1

    .line 891
    move v8, v6

    .line 892
    move/from16 v9, v18

    .line 893
    .line 894
    move-object v6, v0

    .line 895
    invoke-direct/range {v5 .. v11}, Lcom/vk/statistic/DeprecatedStatisticUrl;-><init>(Ljava/lang/String;Ljava/lang/String;IIILcom/vk/statistic/DeprecatedStatisticInterface;)V

    .line 896
    .line 897
    .line 898
    move-object v0, v5

    .line 899
    move-object v5, v11

    .line 900
    iput-object v0, v5, Lcom/vk/dto/newsfeed/entries/PromoPost;->s:Lcom/vk/statistic/DeprecatedStatisticUrl;

    .line 901
    .line 902
    return-object v5
.end method
```

---

## 4. Entry Factory Caller Analysis & Null-Safety Verification

### Exact Caller Methods
1. **Entry Factory**: `Lcom/vk/dto/newsfeed/entries/a;->a(Ljava/lang/String;Lorg/json/JSONObject;Landroid/util/ArrayMap;Landroid/util/SparseArray;Ljava/util/Map;ZLjb2/l;)Lcom/vk/feed/core/models/news/NewsEntry;`
2. **Feed List Builder / Parser**: `Lcom/vk/dto/newsfeed/a;->a(...)` or similar feed parsing loops in `Lcom/vk/dto/newsfeed/a;` (lines 282-303).

### Caller Verification in `com.vk.dto.newsfeed.a`
```smali
    :goto_0
    if-ge v14, v3, :cond_2

    .line 52
    .line 53
    invoke-virtual {v4, v14}, Lorg/json/JSONArray;->getJSONObject(I)Lorg/json/JSONObject;

    .line 54
    .line 55
    .line 56
    move-result-object v7

    .line 57
    :try_start_0
    invoke-static {v2, v7}, Lcx1/s0;->i(Ljava/lang/String;Lorg/json/JSONObject;)Ljava/lang/String;

    .line 58
    .line 59
    .line 60
    sget-object v0, Ljb2/l$a;->a:Ljb2/l$a;

    .line 61
    .line 62
    invoke-virtual {v0}, Ljb2/l$a;->getSTUB()Ljb2/m;

    .line 63
    .line 64
    .line 65
    move-result-object v12

    .line 66
    invoke-virtual {v7, v2}, Lorg/json/JSONObject;->optString(Ljava/lang/String;)Ljava/lang/String;

    .line 67
    .line 68
    .line 69
    move-result-object v6

    .line 70
    invoke-virtual/range {v5 .. v12}, Lcom/vk/dto/newsfeed/entries/a;->a(Ljava/lang/String;Lorg/json/JSONObject;Landroid/util/ArrayMap;Landroid/util/SparseArray;Ljava/util/Map;ZLjb2/l;)Lcom/vk/feed/core/models/news/NewsEntry;

    .line 71
    .line 72
    .line 73
    move-result-object v0

    .line 74
    if-eqz v0, :cond_1

    .line 75
    .line 76
    invoke-interface {v1, v0}, Ljava/util/List;->add(Ljava/lang/Object;)Z
    :try_end_0
    .catchall {:try_start_0 .. :try_end_0} :catchall_0

    .line 77
    .line 78
    .line 79
    goto :goto_1

    .line 80
    :catchall_0
    move-exception v0

    .line 81
```

### Null-Safety Analysis
* When `MyTargetNativeAdEntry$a->a` or `PromoPost$a->a` returns `null` (`const/4 v0, 0x0` / `return-object v0`), `NewsEntry` factory `Lcom/vk/dto/newsfeed/entries/a;->a(...)` returns `null` to the caller.
* In the list builder (`Lcom/vk/dto/newsfeed/a;`), the returned item is checked against `null`:
  ```smali
  invoke-virtual/range {v5 .. v12}, Lcom/vk/dto/newsfeed/entries/a;->a(...)Lcom/vk/feed/core/models/news/NewsEntry;
  move-result-object v0
  if-eqz v0, :cond_1
  invoke-interface {v1, v0}, Ljava/util/List;->add(Ljava/lang/Object;)Z
  ```
* Because `if-eqz v0` guards `List.add()`, returning `null` safely causes the ad entry to be omitted from the feed list without throwing a `NullPointerException` or breaking feed deserialization.

---

## 5. Catalog Ad Banner DTO (`CatalogAdBannerDto`)

### Class Descriptor
`Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;`

### Fields
* `private final slotId:Ljava/lang/Long;` (`@hn/b(value="slot_id")`)
* `public static final CREATOR:Landroid/os/Parcelable$Creator;`

### Complete Smali Body (`CatalogAdBannerDto.smali`)
```smali
.class public final Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;
.super Ljava/lang/Object;
.source "CatalogAdBannerDto.kt"

# interfaces
.implements Landroid/os/Parcelable;


# annotations
.annotation runtime Lkotlin/Metadata;
    d1 = {
        "\u0000\u0010\n\u0002\u0018\u0002\n\u0002\u0018\u0002\n\u0002\u0010\t\n\u0002\u0008\u0007\u0008\u0087\u0008\u0018\u00002\u00020\u0001B\u0013\u0012\n\u0008\u0002\u0010\u0003\u001a\u0004\u0018\u00010\u0002\u00a2\u0006\u0004\u0008\u0004\u0010\u0005R\u001c\u0010\u0003\u001a\u0004\u0018\u00010\u00028\u0006X\u0087\u0004\u00a2\u0006\u000c\n\u0004\u0008\u0003\u0010\u0006\u001a\u0004\u0008\u0007\u0010\u0008\u00a8\u0006\t"
    }
    d2 = {
        "Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;",
        "Landroid/os/Parcelable;",
        "",
        "slotId",
        "<init>",
        "(Ljava/lang/Long;)V",
        "Ljava/lang/Long;",
        "b",
        "()Ljava/lang/Long;",
        "api_release"
    }
    k = 0x1
    mv = {
        0x2,
        0x2,
        0x0
    }
    xi = 0x30
.end annotation


# static fields
.field public static final CREATOR:Landroid/os/Parcelable$Creator;
    .annotation system Ldalvik/annotation/Signature;
        value = {
            "Landroid/os/Parcelable$Creator<",
            "Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;",
            ">;"
        }
    .end annotation
.end field


# instance fields
.field private final slotId:Ljava/lang/Long;
    .annotation runtime Lhn/b;
        value = "slot_id"
    .end annotation
.end field


# direct methods
.method static constructor <clinit>()V
    .locals 1

    .line 1
    new-instance v0, Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto$a;

    .line 2
    .line 3
    invoke-direct {v0}, Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto$a;-><init>()V

    .line 4
    .line 5
    .line 6
    sput-object v0, Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;->CREATOR:Landroid/os/Parcelable$Creator;

    .line 7
    .line 8
    return-void
.end method

.method public constructor <init>()V
    .locals 2

    .line 1
    const/4 v0, 0x0

    const/4 v1, 0x1

    invoke-direct {p0, v0, v1, v0}, Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;-><init>(Ljava/lang/Long;ILkotlin/jvm/internal/DefaultConstructorMarker;)V

    return-void
.end method

.method public constructor <init>(Ljava/lang/Long;)V
    .locals 0

    .line 2
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    .line 3
    iput-object p1, p0, Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;->slotId:Ljava/lang/Long;

    return-void
.end method

.method public synthetic constructor <init>(Ljava/lang/Long;ILkotlin/jvm/internal/DefaultConstructorMarker;)V
    .locals 0

    and-int/lit8 p2, p2, 0x1

    if-eqz p2, :cond_0

    const/4 p1, 0x0

    .line 4
    :cond_0
    invoke-direct {p0, p1}, Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;-><init>(Ljava/lang/Long;)V

    return-void
.end method


# virtual methods
.method public final b()Ljava/lang/Long;
    .locals 1

    .line 1
    iget-object v0, p0, Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;->slotId:Ljava/lang/Long;

    .line 2
    .line 3
    return-object v0
.end method

.method public final describeContents()I
    .locals 1

    .line 1
    const/4 v0, 0x0

    .line 2
    return v0
.end method

.method public final equals(Ljava/lang/Object;)Z
    .locals 3

    .line 1
    const/4 v0, 0x1

    .line 2
    if-ne p0, p1, :cond_0

    .line 3
    .line 4
    return v0

    .line 5
    :cond_0
    instance-of v1, p1, Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;

    .line 6
    .line 7
    const/4 v2, 0x0

    .line 8
    if-nez v1, :cond_1

    .line 9
    .line 10
    return v2

    .line 11
    :cond_1
    check-cast p1, Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;

    .line 12
    .line 13
    iget-object v1, p0, Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;->slotId:Ljava/lang/Long;

    .line 14
    .line 15
    iget-object p1, p1, Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;->slotId:Ljava/lang/Long;

    .line 16
    .line 17
    invoke-static {v1, p1}, Lkotlin/jvm/internal/h;->e(Ljava/lang/Object;Ljava/lang/Object;)Z

    .line 18
    .line 19
    .line 20
    move-result p1

    .line 21
    if-nez p1, :cond_2

    .line 22
    .line 23
    return v2

    .line 24
    :cond_2
    return v0
.end method

.method public final hashCode()I
    .locals 1

    .line 1
    iget-object v0, p0, Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;->slotId:Ljava/lang/Long;

    .line 2
    .line 3
    if-nez v0, :cond_0

    .line 4
    .line 5
    const/4 v0, 0x0

    .line 6
    return v0

    .line 7
    :cond_0
    invoke-virtual {v0}, Ljava/lang/Object;->hashCode()I

    .line 8
    .line 9
    .line 10
    move-result v0

    .line 11
    return v0
.end method

.method public final toString()Ljava/lang/String;
    .locals 3

    .line 1
    new-instance v0, Ljava/lang/StringBuilder;

    .line 2
    .line 3
    const-string v1, "CatalogAdBannerDto(slotId="

    .line 4
    .line 5
    invoke-direct {v0, v1}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V

    .line 6
    .line 7
    .line 8
    iget-object v1, p0, Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;->slotId:Ljava/lang/Long;

    .line 9
    .line 10
    const/16 v2, 0x29

    .line 11
    .line 12
    invoke-static {v0, v1, v2}, Lcom/google/protobuf/r0;->c(Ljava/lang/StringBuilder;Ljava/lang/Long;C)Ljava/lang/String;

    .line 13
    .line 14
    .line 15
    move-result-object v0

    .line 16
    return-object v0
.end method

.method public final writeToParcel(Landroid/os/Parcel;I)V
    .locals 1

    .line 1
    iget-object p2, p0, Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;->slotId:Ljava/lang/Long;

    .line 2
    .line 3
    if-nez p2, :cond_0

    .line 4
    .line 5
    const/4 p2, 0x0

    .line 6
    invoke-virtual {p1, p2}, Landroid/os/Parcel;->writeInt(I)V

    .line 7
    .line 8
    .line 9
    return-void

    .line 10
    :cond_0
    const/4 v0, 0x1

    .line 11
    invoke-static {p1, v0, p2}, La13/j;->c(Landroid/os/Parcel;ILjava/lang/Long;)V

    .line 12
    .line 13
    .line 14
    return-void
.end method

```

### Parcelable Creator (`CatalogAdBannerDto$a.smali`)
```smali
.class public final Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto$a;
.super Ljava/lang/Object;
.source "CatalogAdBannerDto.kt"

# interfaces
.implements Landroid/os/Parcelable$Creator;


# annotations
.annotation system Ldalvik/annotation/EnclosingClass;
    value = Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;
.end annotation

.annotation system Ldalvik/annotation/InnerClass;
    accessFlags = 0x19
    name = "a"
.end annotation

.annotation system Ldalvik/annotation/Signature;
    value = {
        "Ljava/lang/Object;",
        "Landroid/os/Parcelable$Creator<",
        "Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;",
        ">;"
    }
.end annotation


# direct methods
.method public constructor <init>()V
    .locals 0

    .line 1
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    .line 2
    .line 3
    .line 4
    return-void
.end method


# virtual methods
.method public final createFromParcel(Landroid/os/Parcel;)Ljava/lang/Object;
    .locals 3

    .line 1
    new-instance v0, Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;

    .line 2
    .line 3
    invoke-virtual {p1}, Landroid/os/Parcel;->readInt()I

    .line 4
    .line 5
    .line 6
    move-result v1

    .line 7
    if-nez v1, :cond_0

    .line 8
    .line 9
    const/4 p1, 0x0

    .line 10
    goto :goto_0

    .line 11
    :cond_0
    invoke-virtual {p1}, Landroid/os/Parcel;->readLong()J

    .line 12
    .line 13
    .line 14
    move-result-wide v1

    .line 15
    invoke-static {v1, v2}, Ljava/lang/Long;->valueOf(J)Ljava/lang/Long;

    .line 16
    .line 17
    .line 18
    move-result-object p1

    .line 19
    :goto_0
    invoke-direct {v0, p1}, Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;-><init>(Ljava/lang/Long;)V

    .line 20
    .line 21
    .line 22
    return-object v0
.end method

.method public final newArray(I)[Ljava/lang/Object;
    .locals 0

    .line 1
    new-array p1, p1, [Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;

    .line 2
    .line 3
    return-object p1
.end method

```

### Usage in Catalog Section
`CatalogAdBannerDto` is referenced in `Lcom/vk/api/generated/catalog/dto/CatalogSectionDto;` as field `adBanner` (`private final adBanner:Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;`), getter `public final d()Lcom/vk/api/generated/catalog/dto/CatalogAdBannerDto;`, and in the section constructor.
