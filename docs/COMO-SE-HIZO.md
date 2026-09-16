# 🎛️ MIX.CASETE — Cómo se hizo
### *Diario técnico de un reproductor retro que suena sin cuenta, sin servidor y sin rendirse*

<p align="center">

![Android](https://img.shields.io/badge/Plataforma-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![HTML5](https://img.shields.io/badge/HTML5-E34F26?style=for-the-badge&logo=html5&logoColor=white)
![CSS3](https://img.shields.io/badge/CSS3-1572B6?style=for-the-badge&logo=css3&logoColor=white)
![JavaScript](https://img.shields.io/badge/JavaScript-F7DF1E?style=for-the-badge&logo=javascript&logoColor=black)
![Java](https://img.shields.io/badge/Java-WebView%20%2B%20MediaPlayer-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Build](https://img.shields.io/badge/Build-GitHub_Actions-2088FF?style=for-the-badge&logo=githubactions&logoColor=white)
![Sin cuenta](https://img.shields.io/badge/Google_Account-NO_NECESARIA-brightgreen?style=for-the-badge)
![Offline](https://img.shields.io/badge/Modo_OFFLINE-s%C3%AD-blue?style=for-the-badge)
![Gratis](https://img.shields.io/badge/Costo-100%25_GRATIS-success?style=for-the-badge)

</p>

<p align="center">

```text
   ┌─────────────────────────────────────┐
   │ ◉   ┌───────────────────────────┐ ◉ │
   │     │   🎵  M I X . C A S E T E │     │
   │   ┌─┤   ╭───╮   ┌─────┐  ╭───╮  ├─┐ │
   │   │ │   │ ◉ │═══│ ▓▓▓ │══│ ◉ │  │ │ │
   │   └─┤   ╰───   └─────┘  ╰───╯  ├─┘ │
   │ ◉   └───────────────────────────┘ ◉ │
   └─────────────────────────────────────┘
        "La cinta nunca muere, solo rebobina"
```

</p>

---

## 🌟 1. ¿Qué es?

**Mix.Casete** es un reproductor con estética de cassette de los 80 que:

- 🔎 Busca música de YouTube **sin cuenta ni API key**
- 🔊 Reproduce con **cadena de 5 capas anti-caídas**
- 🌙 Suena en **segundo plano y con pantalla bloqueada** (servicio nativo)
- 💾 **Descarga temas** al teléfono para modo offline
- 📼 Guarda playlists con botón **REC** + backup/export/import en Descargas
- 🎤 Muestra **info del tema** con pulsación larga: año, link y **letra**
- 🌙 Tiene **modo oscuro** con cassette negro, persistente
- 🎨 Carátulas HQ con zoom tipo visor (pellizco)
- 📱 Se compila solo a **APK** con GitHub Actions

> [!IMPORTANT]
> Todo es **un HTML + dos clases Java**. Sin frameworks, sin backend, sin pagos.

---

## 🧱 2. Stack

| Capa | Tecnología | Por qué |
|---|---|---|
| 🖥️ UI | HTML + CSS + JS vanilla | Un solo archivo, carga instantánea |
| 🎨 Gráficos | CSS puro + SVG | Sin imágenes: todo vectorial |
| 📱 Contenedor | Android WebView | Web → app nativa |
| 🎵 Audio bg | **MediaPlayer + Foreground Service** | Como Spotify/Brave: audio de sistema |
| 🧠 Extracción | **NewPipeExtractor** (JitPack `dev-SNAPSHOT`) | Descifra firmas de YouTube |
| 🌐 Respaldos | Piped · Invidious · scraping · Jina | Si uno cae, otro responde |
| 📅 Metadatos | iTunes Search (año/carátula) · noembed | Gratis y sin key |
| 🎤 Letras | LRCLIB → lyrics.ovh | APIs públicas con CORS |
| ⚙️ CI/CD | GitHub Actions | Push → APK; tag → Release |
| 💾 Persistencia | localStorage + MediaStore + archivos | Playlist y música offline |

---

## 🗺️ 3. Arquitectura

```text
┌──────────────────────────── TELÉFONO ────────────────────────────┐
│                                                                  │
│  ┌────────────── WebView principal (wv) ─────────────┐           │
│  │            index.html  (toda la UI retro)          │          │
│  │  🔍 Búsqueda ─► Piped ∥ Invidious ∥ YouTube ∥ Jina  │          │
│  │  🎨 Carátula ─► iTunes 2K ∥ maxres ∥ sd ∥ hq         │          │
│  │  📼 Playlist ─► localStorage (+💾) + backup Descargas│          │
│  │  🎤 Info ─► iTunes (año) + LRCLIB/lyrics.ovh (letra) │          │
│  │  🖱️ Pulsación larga ─► panel INFO DEL TEMA           │          │
│  │           │  JavascriptInterface "Android"           │          │
│  └───────────┼──────────────────────────────────────────┘          │
│              ▼                                                     │
│  ┌────────────── MainActivity.java ──────────────────┐            │
│  │  • getStream()  → NewPipe / Invidious / Piped      │            │
│  │  • downloadYT() → descarga .m4a a /Music/          │            │
│  │  • openBrowser() → navegador del teléfono          │            │
│  │  • export/import/loadPlaylistBackup (MediaStore)   │            │
│  │  • playerWv (WebView 1×1 alpha 0 = respaldo audio) │            │
│  └──────────────┬─────────────────────────────────────┘            │
│                 ▼ Intent                                           │
│  ┌────────────── PlaybackService (Foreground) ───────┐            │
│  │  MediaPlayer + WakeLock + AudioFocus               │            │
│  │  Notificación ⏸ ▶ ⏹  →  audio con pantalla OFF     │            │
│  └────────────────────────────────────────────────────┘            │
└──────────────────────────────────────────────────────────────────┘
              ▲ push / tag v*
┌─────────────┴─────────────┐
│  GitHub Actions           │
│  gradle assembleDebug     │
│  → APK en Artifacts       │
│  → Release con tag        │
└───────────────────────────┘
```

---

## 📁 4. Estructura

```text
MixCasete/
├── .github/workflows/android.yml       ⚙️ CI: APK en cada push
├── settings.gradle                     🧩 repos + JitPack
├── build.gradle                        🧩 plugin Android
├── MANUAL-DE-USO.md                    📖 manual del usuario
├── docs/COMO-SE-HIZO.md                📖 este documento
└── app/
    ├── build.gradle                    🧩 + NewPipeExtractor dev-SNAPSHOT
    └── src/main/
        ├── AndroidManifest.xml         🔐 INTERNET + permisos + ícono
        ├── assets/index.html           🎛️ TODA la app web
        ├── java/.../MainActivity.java  🧠 puente + extracción + navegador
        ├── java/.../PlaybackService.java  MediaPlayer + notificación
        ── res/
            ├── drawable/ic_cassette_vector.xml    🎨 ícono vectorial
            ├── mipmap/ic_launcher.xml             🎨 legacy
            ├── mipmap-anydpi-v26/ic_launcher.xml  🎨 adaptativo
            └── values/ (colors.xml, styles.xml)   🎨
```

---

## 🎨 5. UI retro (CSS artesanal)

| Elemento | Técnica |
|---|---|
| Carcasa beige / carbón | Gradientes + textura de puntos |
| Tornillos | `radial-gradient` + ranura `::after` rotada |
| Etiqueta biselada | `clip-path: polygon(...)` |
| Rodillos que giran | SVG + `@keyframes spin` al reproducir |
| Cinta que se consume | `width` del pack = progreso real |
| Teclas piano | Sombras internas + `translateY(3px)` |
| 🌙 Modo oscuro | Clase `body.dark` + overrides (cassette **negro**) |
| Tema persistente | `localStorage('mixcasete_theme')` |

---

## 🔎 6. Búsqueda sin cuenta (4 fuentes en carrera)

```text
   tu texto ──► ┬─ Piped (/search)           ─┐
                ├─ Invidious (/api/v1)        ├─► 🏁 primera que responda
                ├─ YouTube HTML (ytInitialData)─┤
                └─ Jina Reader (markdown→regex)─┘
```

Con proxies CORS (`allorigins`, `codetabs`) en web; **Java consulta directo** (sin CORS).

---

## 🔊 7. Cadena de reproducción (5 capas)

```text
1️⃣ Archivo local 💾        → /Music/<id>.m4a   (OFFLINE)
2️⃣ Cache de stream         → URL < 3 h
3️⃣ Java: NewPipe/instancias → MediaPlayer nativo → SEGUNDO PLANO ✅
4️⃣ Carrera web              → <audio> HTML (escritorio)
5️⃣ WebView watch page       → respaldo audio primer plano
```

---

## 🌙 8. Segundo plano real (como Brave/Spotify)

| Pieza | Rol |
|---|---|
| `PlaybackService` (Foreground) | Mantiene el proceso vivo |
| `MediaPlayer` | Audio de sistema (no depende del WebView) |
| `PARTIAL_WAKE_LOCK` | CPU despierto con pantalla OFF |
| `AudioFocusRequest (USAGE_MEDIA)` | Convive con llamadas/otros apps |
| Notificación ⏸ ▶ ⏹ | Control desde pantalla bloqueada |
| Eventos → JS | Rodillos y auto-siguiente sincronizados |

---

## 🎤 9. Info del tema (pulsación larga ~0,5 s)

| Dato | Fuente |
|---|---|
| 📅 Año de estreno | iTunes Search (`releaseDate`) |
| 🔗 Link YouTube | `watch?v=<id>` + abrir navegador (`Android.openBrowser`) o copiar |
| 🎤 Letra | LRCLIB → fallback lyrics.ovh |

Detección: `pointerdown` + timer 550 ms + cancelación por movimiento (>12 px) + vibración.

---

## 🐛 10. Crónica de batallas

| # | 🐛 Problema | 🛡️ Solución |
|---|---|---|
| 1 | CORS desde `file://` | Proxies CORS + extracción en **Java** |
| 2 | Piped/Invidious muertos | Listas de instancias **vivas** al vuelo |
| 3 | Clients ANDROID capados | **NewPipeExtractor dev-SNAPSHOT** |
| 4 | **Error 153** del embed | Watch page en vez de `/embed/` |
| 5 | WebView mudo en bg | Audio movido a **MediaPlayer nativo** |
| 6 | `MediaStore.Downloads.CONTENT_URI` no existe | `EXTERNAL_CONTENT_URI` |
| 7 | Imports/package duplicados (build roto) | Archivo reescrito limpio |
| 8 | App sin ícono | Vector adaptativo cassette |
| 9 | Volumen no respondía | `setVolumeControlStream(STREAM_MUSIC)` |
| 10 | Letra/año faltantes | Doble fuente con fallback silencioso |

---

## ⚙️ 11. CI/CD

```yaml
push → ubuntu-latest → JDK 17 → gradle assembleDebug
       → APK en "Artifacts"
tag v* → Release automática con APK 🎉
```

---

## 🔮 12. Futuro

- [ ] VU-meters animados
- [ ] Modo "lado B" al finir la lista
- [ ] Exportar playlist a `.m3u`
- [ ] Letras sincronizadas (karaoke)

---

## ⚖️ 13. Nota legal

> [!WARNING]
> Proyecto **educativo**. Respeta derechos de autor y los Términos de YouTube.
> Descargá solo contenido propio, libre o con permiso.

---

<p align="center">

### 📼 *Hecho con paciencia, café y muchos `git push`*
**@Mix.Casete** · Hecho en Uruguay 🇺🇾

</p>
