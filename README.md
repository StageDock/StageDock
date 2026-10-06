# StageDock

Browse and install custom **Synth Riders** stages straight from your Meta Quest. No PC, no cables.

StageDock pulls the stage catalogue from [synthriderz.com](https://synthriderz.com), downloads the Quest version of the stage you pick, and puts it where Synth Riders looks for custom stages.

## Features

- Browse every custom stage with cover art, author and download count
- Search by stage name or author
- Sort by Top, Newest or Most downloaded
- One-tap install, with a progress bar
- See and remove installed stages from the Installed tab
- Launch Synth Riders from the app when you're done
- Stages without a Quest version are clearly marked **PC only**

## Requirements

- Meta Quest 2, Quest Pro, Quest 3 or Quest 3S
- Synth Riders installed on the headset
- An internet connection

## Install

### SideQuest

1. Set up [SideQuest](https://sidequestvr.com/setup-howto) and put your headset in developer mode.
2. Find [StageDock](https://sidequestvr.com/app/62957/stagedock) on SideQuest and install it to your headset.

### Manual

1. Download the latest `StageDock-*-release.apk` from [Releases](https://github.com/StageDock/StageDock/releases).
2. Install it with SideQuest's "Install APK" button, or with `adb install StageDock-x.x.x-release.apk`.

## First launch

1. On your headset, open **Library**, switch the filter to **Unknown Sources**, and start **StageDock**.
2. Tap **Grant access**, then turn on **Allow access to manage all files** for StageDock and go back to the app.

StageDock needs this permission to save stages into the `SynthRidersUC` folder that Synth Riders reads from. It only writes to `SynthRidersUC/CustomStages`.

If the settings screen doesn't open on your headset, grant the permission from a PC instead:

```
adb shell appops set --uid com.stagedock.app MANAGE_EXTERNAL_STORAGE allow
```

## Using StageDock

1. Browse or search for a stage and tap **Install**.
2. When you're done, tap **Play**, or fully close and reopen Synth Riders.
3. Pick your new stage from the stage selection in Synth Riders.

To remove a stage, open the **Installed** tab and tap the bin icon next to it.

## Troubleshooting

**Install button is greyed out.** All files access hasn't been granted yet. See [First launch](#first-launch).

**A stage says "PC only".** The author hasn't uploaded a Quest (`.stagedroid`) version of that stage, so it can't run on Quest.

**Installed stage doesn't show up in Synth Riders.** Close Synth Riders completely and start it again. It only scans for new stages on launch.

**Install failed.** Check the headset is online and try again. If it keeps failing, open an [issue](https://github.com/StageDock/StageDock/issues) with the stage name and the error message.

## Where files go

Stages are saved to `/sdcard/SynthRidersUC/CustomStages/` as `.stagedroid` files. You can also manage them there with SideQuest's file manager.

## Building from source

See [DEVELOPMENT.md](DEVELOPMENT.md).

## Credits

- Stage catalogue and downloads from [synthriderz.com](https://synthriderz.com). Thanks to the stage creators who share their work there.
- Synth Riders is made by Kluge Interactive.

## Disclaimer

StageDock is an unofficial community tool. It is not affiliated with or endorsed by Kluge Interactive or synthriderz.com. Use at your own risk.
