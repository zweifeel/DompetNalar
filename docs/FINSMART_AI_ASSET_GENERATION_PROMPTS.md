# FINSMART — DOKUMEN PROMPT GENERASI VISUAL ASSET AI
**Dokumen:** `docs/FINSMART_AI_ASSET_GENERATION_PROMPTS.md`  
**Versi:** 1.0.0  
**Tanggal:** 10 Oktober 2026  
**Standar Desain:** FinSmart Premium Financial Design System (Emerald Green `#10B981`, Deep Navy/Slate `#0F172A`, Gold Accent `#F59E0B`, Soft Cream/White Background)

---

## 1. Panduan Konsistensi Visual (Art Direction & Design Tokens)

1. **Gaya Visual (Aesthetics):** Modern 3D Claymorphism / Clean Isometric Minimalist dengan pencahayaan lembut (*soft studio lighting*), sudut membulat elegan (*smooth rounded bevels*), dan palet warna finansial terkurasi.
2. **Aturan Teks:** **JANGAN MEMASUKKAN TEKS PANJANG PADA GAMBAR.** Seluruh teks label, angka nominal, dan penjelasan akan dirender secara dinamis oleh Jetpack Compose UI untuk menjamin lokalisasi dan keterbacaan tajam di semua DPI layar.
3. **Optimasi Android:** Format gambar raster target adalah **WebP (Lossless)** atau **PNG Transparan**, dioptimalkan di bawah 150 KB per asset untuk menjaga ukuran APK/AAB tetap ramping.

---

## 2. Kumpulan Prompt Generasi Asset AI

---

### PROMPT 1: App Launcher Icon — Foreground
* **ID Asset:** `AST-ICN-01`
* **Nama File Disarankan:** `ic_launcher_foreground_art.png`
* **Lokasi Resource:** `app/src/main/res/drawable/` & `mipmap-anydpi-v26/`
* **Dimensi & Rasio:** 1024 x 1024 px (Rasio 1:1, berada di dalam safe zone lingkaran tengah 66%)
* **Format:** PNG dengan Background Transparan (Alpha channel)
* **Tema:** Universal (Elegan di latar gelap maupun terang)

```text
Prompt:
High-resolution 3D icon of a modern financial growth emblem. A stylized dynamic green geometric leaf seamlessly morphing into a shiny golden shield and an upward-trending arrow. Premium glassmorphism and subtle matte metallic finish, glowing emerald green (#10B981) and radiant gold (#F59E0B) accents. Perfectly centered, isolated on a pure transparent background, zero drop shadows outside the icon boundary, strictly inside a circular safe zone, 8k render, Unreal Engine 5 aesthetic, clean app icon asset.

Negative Prompt:
realistic photo, noisy, background elements, square frame, border, text, typography, letters, watermark, blurry edges, clipping outside circle, complex details.
```

---

### PROMPT 2: App Launcher Icon — Background
* **ID Asset:** `AST-ICN-02`
* **Nama File Disarankan:** `ic_launcher_background_art.png`
* **Lokasi Resource:** `app/src/main/res/drawable/` & `mipmap-anydpi-v26/`
* **Dimensi & Rasio:** 1024 x 1024 px (Rasio 1:1)
* **Format:** PNG / Solid WebP
* **Tema:** Dark Mode Luxury

```text
Prompt:
Seamless luxury dark financial background pattern, deep midnight navy blue and slate (#0F172A) subtle radial gradient, very faint abstract isometric geometric grid lines with soft dark teal illumination, smooth modern texture, minimalist luxury tech wallpaper, perfectly symmetrical, vector-like cleanliness.

Negative Prompt:
bright colors, complex objects, icons, text, symbols, harsh highlights, center objects, noise, grain.
```

---

### PROMPT 3: Android 13+ Themed Monochrome Icon
* **ID Asset:** `AST-ICN-03`
* **Nama File Disarankan:** `ic_launcher_monochrome.xml` (dibuat via trace vector dari siluet PNG)
* **Dimensi & Rasio:** 512 x 512 px (1:1)
* **Format:** Siluet Hitam Putih / Mask SVG

```text
Prompt:
Minimalist flat vector stencil silhouette of a financial shield with an upward chevron arrow and small leaf motif. Pure solid white shape on a pure solid black background, crisp edges, high contrast, flat graphic design, zero gradients, zero shadows, SVG-ready logo stencil.

Negative Prompt:
color, shading, 3d, realistic, gradients, bevel, noise, text, thin lines.
```

---

### PROMPT 4: Splash Screen Brand Identity Vector
* **ID Asset:** `AST-SPL-01`
* **Nama File Disarankan:** `splash_branding_logo.png`
* **Lokasi Resource:** `app/src/main/res/drawable/`
* **Dimensi & Rasio:** 512 x 512 px (1:1)
* **Format:** PNG Transparan

```text
Prompt:
Clean and elegant FinSmart logo symbol, vibrant emerald green shield enclosing a golden sprout and subtle infinity loop of wealth flow, modern fintech brand identity, floating gracefully, smooth soft lighting, studio render, completely isolated on transparent background.

Negative Prompt:
letters, text, words "FinSmart", background, shadows on edges, box frame.
```

---

### PROMPT 5: Ilustrasi Onboarding 1 — Privacy & Offline-First
* **ID Asset:** `AST-ONB-01`
* **Fitur:** Onboarding Screen (Slide 1)
* **Nama File Disarankan:** `ill_onboarding_privacy.webp`
* **Dimensi & Rasio:** 1080 x 1080 px (1:1)
* **Format:** WebP Transparan

```text
Prompt:
Charming modern 3D illustration representing ultimate financial privacy and offline security. A cute metallic golden safe vault lock gently secured with an emerald green digital key, glowing shield floating nearby, floating SQLite database cube with a small padlock, soft pastel emerald and slate lighting, clean isometric view, minimalist composition, transparent background.

Negative Prompt:
realistic people, creepy face, cluttered scene, text, words, labels, watermark, harsh shadows.
```

---

### PROMPT 6: Ilustrasi Onboarding 2 — Pemindai Nota AI Vision
* **ID Asset:** `AST-ONB-02`
* **Fitur:** Onboarding Screen (Slide 2)
* **Nama File Disarankan:** `ill_onboarding_ai_ocr.webp`
* **Dimensi & Rasio:** 1080 x 1080 px (1:1)
* **Format:** WebP Transparan

```text
Prompt:
Delightful 3D isometric illustration of smart grocery receipt scanning. A stylized paper receipt floating under a glowing cyan and emerald laser scan beam, tiny colorful coins and currency tags emerging into a digital ledger, sleek futuristic smartphone camera lens glowing above, clean tech fintech style, isolated on transparent background.

Negative Prompt:
messy papers, real text on receipt, readable letters, realistic camera, dark background.
```

---

### PROMPT 7: Ilustrasi Onboarding 3 — Rencana Bebas Utang
* **ID Asset:** `AST-ONB-03`
* **Fitur:** Onboarding Screen (Slide 3)
* **Nama File Disarankan:** `ill_onboarding_freedom.webp`
* **Dimensi & Rasio:** 1080 x 1080 px (1:1)
* **Format:** WebP Transparan

```text
Prompt:
Inspiring 3D illustration symbolizing debt freedom and financial peace of mind. A broken heavy metal chain turning into golden sparkles, an upward staircase leading to a glowing green mountain peak with a calm rising sun, coins stacking neatly with zero burden, optimistic and uplifting atmosphere, isolated on transparent background.

Negative Prompt:
stressful symbols, handcuffs, prison, red alerts, text, messy elements.
```

---

### PROMPT 8: Empty State — Belum Ada Transaksi
* **ID Asset:** `AST-EMP-01`
* **Fitur:** Dashboard & Transaksi Screen
* **Nama File Disarankan:** `ill_empty_transactions.webp`
* **Dimensi & Rasio:** 600 x 600 px (1:1)
* **Format:** WebP Transparan

```text
Prompt:
Friendly minimalist 3D clay illustration of an empty financial notepad with a cheerful clean pencil resting beside it, a lone shiny coin floating nearby with tiny soft sparkles, soft muted teal and gray palette, welcoming and clean aesthetic, isolated on transparent background.

Negative Prompt:
busy background, complicated charts, realistic objects, text, writing on notepad.
```

---

### PROMPT 9: Empty State — Bebas Utang (Zero Debt)
* **ID Asset:** `AST-EMP-03`
* **Fitur:** Debt Screen & Debt Freedom Planner
* **Nama File Disarankan:** `ill_empty_debts.webp`
* **Dimensi & Rasio:** 600 x 600 px (1:1)
* **Format:** WebP Transparan

```text
Prompt:
Joyful 3D financial illustration celebrating zero debt. A smiling golden piggy bank wearing a celebratory small golden crown, surrounded by green olive leaves of victory and soft floating confetti, peaceful and happy mood, bright clean lighting, isolated on transparent background.

Negative Prompt:
unhappy expressions, text, coins spilling everywhere, red colors, realistic textures.
```

---

### PROMPT 10: Gamifikasi Badges (Streak Milestones)
* **ID Asset:** `AST-GAM-01` s/d `AST-GAM-04`
* **Fitur:** Hub Gamifikasi & Notifikasi
* **Dimensi & Rasio:** 512 x 512 px (1:1)
* **Format:** PNG Transparan

#### A. Rookie Planner (Streak 7 Hari)
```text
Prompt:
Gamification reward medal badge, bronze and emerald circular shield with a small vibrant flame icon in the center, shiny metallic rim with laurel leaves, video game achievement icon style, clean vector-like 3D render, isolated on transparent background.
```

#### B. Budget Master (Streak 30 Hari)
```text
Prompt:
Prestigious gamification badge medal, gleaming polished silver and emerald green star medallion, glowing double flame in center, embossed golden ribbon at bottom, high detail 3D render, video game trophy icon, isolated on transparent background.
```

#### C. Financial Guru (Streak 90 Hari)
```text
Prompt:
Epic gamification achievement crest, glowing radiant gold shield with an intricate diamond flame in center, surrounded by ornate golden wings, brilliant emerald jewels inlaid, premium royal financial award, isolated on transparent background.
```

#### D. Centurion FinSmart (Streak 365 Hari)
```text
Prompt:
Legendary masterclass financial trophy badge, shimmering holographic platinum and obsidian medallion with a burning eternal golden flame, crown motif on top, glowing particle aura, ultimate game achievement badge, isolated on transparent background.
```

---

### PROMPT 11: Avatar AI Financial Advisor
* **ID Asset:** `AST-AI-01`
* **Fitur:** `AiChatScreen`
* **Nama File Disarankan:** `avatar_gemini_advisor.webp`
* **Dimensi & Rasio:** 256 x 256 px (1:1)
* **Format:** WebP Transparan

```text
Prompt:
Cute friendly modern 3D AI robot mascot head, smooth matte white ceramic shell with glowing emerald green curved visor eyes, smiling benevolent expression, subtle golden antenna detail, futuristic yet approachable financial assistant, isolated on transparent background.

Negative Prompt:
creepy robot, red eyes, sharp scary edges, realistic human, complex background.
```

---

### PROMPT 12: Paywall Header Banner (Smart Master Gold)
* **ID Asset:** `AST-PAY-01`
* **Fitur:** `SmartMasterPaywallDialog`
* **Nama File Disarankan:** `banner_smart_master_gold.webp`
* **Dimensi & Rasio:** 1200 x 600 px (Rasio 2:1)
* **Format:** WebP

```text
Prompt:
Luxury financial header banner background, rich dark emerald and obsidian gradient background with soft glowing golden bokeh and floating 3D golden coins, sleek credit card silhouette with a golden crown emblem on the right side, elegant curved glassmorphic ribbons, spacious left side for text overlay, premium fintech aesthetic.

Negative Prompt:
harsh text, realistic faces, stock photos, clutter, watermark, cartoonish style.
```

---
*Seluruh prompt di atas siap digunakan pada tool generasi gambar AI (Imagen 3 / Midjourney / DALL-E) dengan hasil yang konsisten terhadap identitas FinSmart.*
