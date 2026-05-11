# Product Spec

## Users

- Frontline shipyard workers using Android phones in weak-network environments
- Internal operators managing accounts, form permissions, and reset actions

## Core goals

- Collect data offline first
- Cache up to 500 local records per device
- Upload later when network conditions improve
- Confirm upload by final ChuangYun write result
- Keep interaction simple, large-target, and worker-friendly

## MVP flows

### Login

- First login must be online
- Login uses phone number and initial password
- Password can be changed later
- Offline reuse is allowed for the last successful login account
- Offline validity is 30 days

### Capture

- User selects a form before capture
- Every record belongs to one form
- Unified record structure for every form:
  - `location_name` required
  - 1 to 5 photos required
  - 0 or 1 audio note, max 60 seconds
  - optional text note, max 200 chars
- Photos can be taken from camera or chosen from gallery
- Basic image editing in v1:
  - rotate
  - replace
  - delete
  - crop

### Upload

- Upload is manual-first
- Network recovery may prompt the user, but does not auto-upload
- Upload runs through an Android foreground service and may continue in background/lock screen
- Users can pause, resume, or cancel a batch
- New records created during an upload batch are not auto-added to the active batch
- Success is only confirmed after backend writes to ChuangYun
- Failed items stay local and are retried later
- Visible record statuses:
  - pending
  - uploading
  - failed
  - uploaded

### Local retention

- 500-record cap is based on record count, not file count
- At 500 records the app blocks new record creation
- Uploaded records stay local until the user clears them
- Unuploaded records may be deleted with a strong confirmation

## Permissions

Minimal v1 permission model:

- account enabled/disabled
- allowed forms
- may upload
- may delete local cache

## Platform integration

- App uploads to our backend
- Backend owns ChuangYun adapter logic
- Mock adapter is acceptable before the real API docs arrive

## Metadata

Every record should include:

- `record_id`
- `form_id`
- device id
- capture time
- upload time
- uploader phone number and display name
