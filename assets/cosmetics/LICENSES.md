# Phoenix cosmetic asset licence ledger

This ledger is the required source-of-truth for every third-party model, texture,
animation, sound or particle asset shipped with Phoenix.

## Current import status

No third-party 3D model or animation has been imported into the APK by this
change. The catalogue uses text/emoji placeholders only. This is intentional:
a download page or a creator's "free" label is not sufficient proof that an
asset may be redistributed inside a launcher.

## Required record for every imported asset

| Field | Required value |
|---|---|
| Cosmetic ID | Stable catalogue ID |
| Source | Exact creator page / repository URL |
| Creator | Creator's displayed name |
| Licence | Exact SPDX identifier or verbatim custom licence name |
| Licence URL | Exact licence text URL |
| Attribution | Required credit, or "No attribution required" only when licence confirms it |
| SHA-256 | Hash of the exact imported file |
| Redistribution | Explicitly allowed / not allowed |
| Modifications | Whether edits, retargeting, texture changes or animation changes were made |

## Import policy

- Prefer CC0 / public-domain assets, or assets whose licence explicitly allows
  redistribution in an application and modification.
- Keep the original licence and attribution text beside the asset.
- Never import "personal use only", "non-commercial only", or unclear assets.
- Do not bundle a Blockbench Workshop model until its individual creator licence
  has been checked.
- Reject assets with missing source, licence, attribution or checksum.
- Do not use Mojang/Microsoft-owned character or promotional assets without
  permission.

## Empty manifest

There are currently no imported model files, so there are no third-party
licence records to claim. Add one complete record above for each future asset.
