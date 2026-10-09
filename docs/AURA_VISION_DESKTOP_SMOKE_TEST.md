# AURA Vision — Desktop smoke-test checklist

Use this checklist on a real Windows desktop before calling the camera preview physically validated. CI verifies compilation and automated tests; it cannot verify a user's camera, driver, or privacy settings.

## Discovery and privacy

- [ ] Start AURA with a webcam attached. Confirm startup does not open the camera indicator or reserve the device.
- [ ] Choose **Pindai Kamera**. Confirm each OS-visible camera is listed and no camera is opened merely by scanning.
- [ ] Test with no camera connected. AURA should show a useful Indonesian message rather than crash.
- [ ] Test with Windows camera privacy access disabled. AURA should report that capture cannot start and remain usable.

## Preview lifecycle

- [ ] Choose **Mulai Kamera**, select a device, and confirm the preview window displays live frames.
- [ ] If the camera takes time to produce a frame, confirm the window shows a waiting message instead of remaining indefinitely on a generic loading label.
- [ ] Test with two available cameras and confirm the selected device is the one shown.
- [ ] Close the preview window using its close control. Confirm capture stops and the device can be opened by another application.
- [ ] Start preview again, then choose **Hentikan Kamera**. Confirm the device is released.
- [ ] Disconnect the USB webcam while preview is running. Confirm the preview reports the error and AURA remains responsive.
- [ ] Test while another application is already using the webcam. Confirm a clear Indonesian failure message is shown.

## Package validation

- [ ] Install the EXE on a clean Windows user account or test VM.
- [ ] Launch AURA from the installed shortcut and repeat the discovery/preview lifecycle checks.
- [ ] Confirm the Surya Majapahit-inspired avatar appears and normal AURA controls remain accessible.
- [ ] Record Windows version, camera make/model, and any driver/privacy errors with the test result.

A release should not be described as physically webcam-validated until these checks have been performed on real hardware.
