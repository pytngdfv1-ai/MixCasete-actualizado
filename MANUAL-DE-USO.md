# 📲 MIX.CASETE — Manual de Instalación y Uso
### *Tu mixtape infinito: sin cuenta, sin servidor, sin mensualidades*

<p align="center">

![APK](https://img.shields.io/badge/Descarga-APK_directo-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Sin cuenta](https://img.shields.io/badge/Google_Account-NO-brightgreen?style=for-the-badge)
![Offline](https://img.shields.io/badge/Modo_OFFLINE-disponible-blue?style=for-the-badge)
![2º plano](https://img.shields.io/badge/Segundo_plano-s%C3%AD-purple?style=for-the-badge)
![Precio](https://img.shields.io/badge/Precio-GRATIS_SIEMPRE-success?style=for-the-badge)

</p>

<p align="center">

```text
 ╔═══════════════════════════════════════════╗
 ║  🎵 BUSCA → SUENA → GRABA → DESCARGA 🎙️   ║
    …como un cassette, pero del futuro      ║
 ═══════════════════════════════════════════╝
```

</p>

---

## 🚀 1. Instalación

### Opción A — APK desde GitHub Actions *(la fácil)* ⭐
1. Repo → pestaña **Actions**
2. Abrí el último run verde ✅ **"Build Mix.Casete APK"**
3. Bajá hasta **Artifacts** → `MixCasete-APK`
4. Descomprimí → `MixCasete.apk` → pasalo al teléfono
5. Tocá el APK → si protesta: **Permitir apps desconocidas**
6. 🎉 Abrí **Mix.Casete**

### Opción B — Release oficial 🏷️
```bash
git tag v1.0
git push origin v1.0
```
→ GitHub crea una **Release** con el APK adjunto.

### Opción C — Android Studio 🛠️
1. Cloná el repo y abrilo
2. Sync de Gradle (baja NewPipeExtractor de JitPack)
3. **Build → Build APK(s)** → `app/build/outputs/apk/debug/`

### Opción D — Navegador (solo vitrina) 🌐
```bash
python -m http.server 8080
```
→ `http://localhost:8080` (sin descargas ni segundo plano; la app completa vive en el APK)

---

## 🎛️ 2. Tour por la interfaz

```text
 ┌──────────────────────────────┐
 │                          (🌙) │ ← tema claro/oscuro
 │  ┌────────────────────────┐  │
 │  │      🎨 CARÁTULA       │  │ ← tocá: zoom fullscreen
 │  │                        │  │   (pellizco = zoom)
 │  └────────────────────────┘  │
 │  ┌────────────────────────┐  │
 │  │  Título del tema       │  │
 │  │  ┌──┐  ╭─╮     ╭─╮  60│  │ ← rodillos giran al sonar
 │  │  │A │  │◉│═════│◉│MIN│  │
 │  │  └──┘  ╰─╯     ╰─╯    │  │
 │  │   ●  Artista actual   │  │
 │  └────────────────────────┘  │
 │  [ 🔍 SEARCH · BUSCAR ]  🟢  │ ← LED: 🟢 nativo · 🟠 respaldo
 │  RECORD PLAY REW FF STOP PAUSE│
 │  [ 📼 PLAYLIST · 3 TEMAS  3 ] │
 └──────────────────────────────┘
                          (🔊)  ← emergencia de sonido
```

---

## 🎵 3. Uso básico

| Acción | Cómo |
|---|---|
| 🔎 Buscar | `🔍 SEARCH` → escribí → **OK** → tocá el resultado |
| 🔗 Pegar link | Campo 2 → **▶** |
| ▶ ⏸ ⏹ ◀ ▶▶ | Teclas piano (rew/ff = ±10 s) |
| 🎚️ Volumen | **Botones físicos** del teléfono |
| 🌙/☀️ Tema | Botón redondo arriba a la derecha (se recuerda) |
| 🔍 Carátula | Tocá la carátula → zoom; pellizco/＋－⟲; ✕ salir |

---

## 🎙️ 4. REC, playlist y offline

1. Poné el tema → tocá **● RECORD**
2. Verás `● REC → tema guardado ✔` y `⏬ Descargando tema...`
3. En 📼 PLAYLIST:
   - **💾** al lado = descargado → suena **sin internet**
   - ▶ reproduce · ▲▼ reordena · ✕ quita · 🗑 vacía

### Exportar / Importar
| Botón | Qué hace |
|---|---|
| ⬆ EXPORTAR | Guarda `MixCasete_playlist.json` en **Descargas** |
| ⬇ IMPORTAR | Elige un `.json` con el picker del sistema |

> [!TIP]
> El backup se actualiza solo cada vez que cambia la playlist.
> Si reinstalás la app, al abrirla recupera la playlist desde Descargas.

---

## 🎤 5. Info del tema (pulsación larga)

**Mantené el dedo apretado (~0,5 s)** sobre un tema de la playlist → vibra y abre:

| Dato | Detalle |
|---|---|
| 🖼️ Carátula + título + artista | Del registro |
| 📅 Año de estreno | Buscado en iTunes |
| 🔗 Link YouTube | Visible completo |
| ▶ VER EN YOUTUBE | Abre el navegador del teléfono |
| 📋 COPIAR LINK | Al portapapeles con confirmación |
| 🎤 LETRA | LRCLIB → lyrics.ovh (scrolleable) |

Deslizá el dedo durante la pulsación para cancelarla. ✕ o tocar afuera cierra.

---

## 🌙 6. Segundo plano y pantalla bloqueada

- Minimizá la app o bloqueá la pantalla → **sigue sonando** ✅
- Aparece una **notificación** con:
  - ⏸ Pausa / ▶ Seguir / ⏹ Parar
  - Tocá la notificación → vuelve a la app
- Auriculares Bluetooth: play/pause físico funciona
- Si tu Xiaomi/Huawei lo corta: Ajustes de batería → app → **"Sin restricciones"**

---

## 🧭 7. Solución de problemas

| 🐛 Síntoma | ✅ Remedio |
|---|---|
| No suena al tocar | Tocá **🔊** una vez; subí volumen físico |
| Se corta en bg | Batería → app → "Sin restricciones" |
| "Búsqueda saturada" | Reintentá o pegá el link directo |
| Sin 💾 al grabar | La extracción falló ese día; el tema queda en playlist |
| Letra no aparece | Puede no existir en LRCLIB/lyrics.ovh |
| Año en "—" | iTunes no tiene ese tema |
| Playlist vacía tras reinstalar | ⬇ IMPORTAR el `.json` de Descargas |

---

## ❓ 8. Preguntas frecuentes

**¿Necesito cuenta de Google?** No. Nunca.
**¿Funciona sin internet?** Los temas con 💾 sí.
**¿Es gratis?** 100 %. Sin ads, sin trackers, sin suscripciones.
**¿Mis datos salen del teléfono?** Solo consultas de búsqueda/reproducción. Playlist y música quedan locales.
**¿Puedo usarla en el navegador?** Como vitrina sí; la experiencia completa (offline, 2º plano, descargas) vive en el APK.

---

## 📂 9. ¿Dónde queda mi música?

```text
Música:   Android/data/com.mixcasete.app/files/Music/<id>.m4a
Playlist: Descargas/MixCasete_playlist.json
Tema:     guardado en el teléfono (botón 🌙/☀️)
```

---

## ⚖️ 10. Aviso legal

> [!WARNING]
> Proyecto **educativo**. Respeta los derechos de autor y los Términos de
> YouTube. Descargá únicamente contenido propio, libre o con permiso.

---

<p align="center">

### 🎧 *Dale PLAY. El resto es nostalgia.*
**@Mix.Casete** 📼🇺🇾

</p>
