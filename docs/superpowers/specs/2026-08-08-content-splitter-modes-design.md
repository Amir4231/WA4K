# Design: Content Splitter Modes (Auto-Split / Manual Select)

## Overview

Add two new content-splitting modes alongside the existing Auto-Split mode, giving users control over what portion of their media gets posted to WhatsApp status.

## Modes

### Auto (existing, unchanged)
Video is automatically split into 30-second segments. All clips shown in Results, all shareable.

### ManualTrim (new)
User picks a single 30-second window from the original video using a draggable range slider on a full-video timeline. Only that portion is processed.

### ManualSegments (new)
Auto-split runs first, then the user checks/unchecks which segments to keep. "Share Selected" sends only checked clips.

## Architcture

### New UI States

Added to `UploaderUiState` sealed interface:

- `Idle` — gains `splitMode: SplitMode` field
- `Picked` — gains `splitMode: SplitMode` field (toggle-able in bottom sheet)
- `TrimEditing` — new: full-screen trim UI with range slider timeline
- `SegmentResults` — new: auto-split results with checkboxes + "Share Selected"
- `Results` — unchanged
- `BatchResults` — unchanged
- `Processing` — unchanged
- `Error` — unchanged
- `Loading` — unchanged

### New / Modified Models

```kotlin
enum class SplitMode { Auto, ManualTrim, ManualSegments }
```

`ProcessedClip` gains a `val selected: Boolean = true` field used by ManualSegments mode. A clip with `selected = false` is excluded from "Share Selected" but still shown in the results list.

### Edge Cases

- **Video ≤ 30s in ManualSegments mode**: Processes as a single segment, shown as one checked clip. Effectively identical to Auto.
- **Video ≤ 30s in ManualTrim mode**: Range slider spans 0 to videoDuration (max 30s). User can still trim a sub-portion.
- **Mode switch mid-flow**: Toggling mode in the PickedScreen sheet only takes effect on the *next* "Process" tap. Does not retroactively change the current screen.
- **"Simplified PickedScreen" for ManualSegments**: Same settings sheet (preset, filter, enhancement toggles) but without trim controls, since trimming is not applicable.

### Flow by Mode

| Mode | Pick → | Screen | Process → | Results |
|------|--------|--------|-----------|---------|
| Auto | Picker | PickedScreen (settings sheet) | Full → N clips | Results (share each/all) |
| ManualTrim | Picker | TrimEditingScreen | Trim range → 1 clip | Results (single) |
| ManualSegments | Picker | PickedScreen (simplified) | Full → N clips | SegmentResults (checkboxes) |

## Components

### New

| Component | Package | Purpose |
|-----------|---------|---------|
| `SplitMode` enum | `model/` | Mode enum |
| `SplitModeSelector` | `ui/selector/` | Material3 SegmentedButton for 3 modes, used on IdleScreen and PickedScreen sheet |
| `TrimEditingScreen` | `ui/trim/` | Full-screen trim editor shell |
| `TrimRangeSlider` | `ui/trim/` | Draggable range slider composable (video thumbnail strip + start/end handles, max 30s window) |

### Modified

| Component | Changes |
|-----------|---------|
| `VideoUploaderViewModel` | Add `currentSplitMode: MutableStateFlow<SplitMode>`, trim start/end state, new state transitions |
| `StatusUploaderScreen` | Route `TrimEditing` and `SegmentResults` states to new composables |
| `IdleScreen` (inline) | Add `SplitModeSelector` below hero area |
| `PickedScreen` bottom sheet | Add `SplitModeSelector` at top of sheet |
| `ResultsScreen` | Add checkbox variant for `SegmentResults` state with "Share Selected (N)" button |
| `ProcessVideoUseCase` | Accept `splitMode`, `trimStartMs`, `trimEndMs` params; branch logic |
| `FfmpegCommandBuilder` | Support single-segment trim via `-ss {start}s -t {duration}s` |
| `ShareManager` | Add `buildShareSelectedIntent()` for subset of clips |

## TrimEditingScreen Layout

Full-screen, replaces PickedScreen for ManualTrim mode.

| Area | Content |
|------|---------|
| Top bar | Back arrow, "Select 30s clip", confirm button |
| Center (70%) | Video preview — plays selected range, tap to play/pause |
| Info row | Timecode: `01:23 — 01:53 (30s)` |
| Bottom (30%) | `TrimRangeSlider`: thumbnail strip via Coil VideoFrameDecoder, two draggable handles, blue overlay between them. Max 30s window — dragging one handle pushes the other. Tap outside range to preview from that point. |

## SegmentResultsScreen Layout

Similar to current ResultsScreen but with Material3 Checkbox per clip card.

| Area | Content |
|------|---------|
| Top bar | "Select clips to share" + "Share Selected (N)" button |
| List | Clip cards: thumbnail, duration, size, checkbox (all checked default) |
| FAB | Triggers share |

## Data Flow

### ManualTrim
1. User picks video in ManualTrim mode → TrimEditingScreen
2. TrimRangeSlider updates `trimStartMs` / `trimEndMs` in ViewModel
3. User confirms → ViewModel calls `ProcessVideoUseCase(uri, SplitMode.ManualTrim, trimStartMs, trimEndMs, settings)`
4. UseCase delegates to `FfmpegCommandBuilder` with single-segment command
5. Single `ProcessedClip` returned → Results screen

### ManualSegments
1. User picks video in ManualSegments mode → PickedScreen (no trim)
2. User presses Process → `SplitVideoUseCase` runs, FFmpeg processes all segments
3. `ProcessedClip` items carry `selected: Boolean = true`
4. Results display with checkboxes → user toggles → ShareManager shares only `selected` clips

## Mode Selector UI

Material3 `SegmentedButton` with 3 options, icons:

- "Auto Split" — scissor icon
- "Trim Clip" — cut icon
- "Pick Segments" — checklist icon

Selected option highlighted in WhatsApp green (`Green40`). Placed on:
1. IdleScreen, below gradient hero
2. PickedScreen bottom sheet, above preset selector

Mode persists only in ViewModel memory (VM scope).

## Processing Pipeline Changes

`ProcessVideoUseCase.invoke()` signature:

```kotlin
suspend operator fun invoke(
    uri: Uri,
    splitMode: SplitMode,
    trimStartMs: Long = 0,
    trimEndMs: Long = 0,
    settings: ProcessingSettings
): ProcessResult
```

Internal branching:
- `Auto` / `ManualSegments`: run `SplitVideoUseCase` → process all segments
- `ManualTrim`: skip SplitVideoUseCase, process single segment from trimStartMs to trimEndMs

`FfmpegCommandBuilder` new method: `buildTrimCommand(input, output, startMs, endMs, settings)` using `-ss` / `-t` without segment loop.

## Testing

- `SplitModeSelector` preview composable test
- `FfmpegCommandBuilderTest`: add test for trim command format (verify `-ss` and `-t` in output)
- ViewModel unit test: verify state transitions for each mode path
- Manual QA: verify WhatsApp sharing with selected-only clips in ManualSegments mode
