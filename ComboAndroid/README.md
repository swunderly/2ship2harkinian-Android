# ComboShip Android port — development branch

This directory is a source integration overlay for Varuuna/ComboShip at 94eb185e4abcc2d568aa8241fa02c43cdd86c439. It keeps the real two-game runtime and combined randomizer rather than wrapping two standalone apps.

**Status: experimental port under construction; no working APK or complete QoL parity is claimed.** The native pipeline compiles actual game libraries, not stubs. Device gameplay, graphics, transitions, save recovery, and feature parity require validation.

The planned app identity is `org.comboship.android`, separate from `com.twoshipfork.mm`. Existing 2S2H data must never be reset, overwritten, or automatically migrated. ROMs are supplied by the user; no game ROMs or ROM-derived game archives are included in the repository or build artifacts.

Use `python3 ComboAndroid/tools/apply_port.py /path/to/fresh/pinned/ComboShip` to stage the overlay and apply checked Android-specific patches. It refuses a wrong revision, tracked edits, or an already-patched tree.

Source licenses remain those of each upstream component. Added port-specific code is available under MIT; this does not relicense the upstream games, engine, or dependencies.
