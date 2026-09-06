from pathlib import Path

path = Path("app/src/main/java/com/atuy/scomb/ui/features/SettingsScreen.kt")
text = path.read_text(encoding="utf-8")

text = text.replace("import androidx.compose.foundation.layout.weight\n", "")
text = text.replace("PackageManager.PackageInfoFlags.of(0)", "PackageManager.PackageInfoFlags.of(0L)")

old = '''    val commitHash = remember(context) {
        context.packageManager.getApplicationInfo(
            context.packageName,
            PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
        ).metaData?.getString("com.atuy.scomb.GIT_COMMIT_HASH").orEmpty().take(7)
    }
'''
new = '''    val commitHash = remember(context) {
        runCatching {
            context.assets.open("build_commit.txt").bufferedReader().use { reader ->
                reader.readText().trim()
            }
        }.getOrDefault("local").take(7)
    }
'''
if old not in text:
    raise SystemExit("commitHash block not found")
text = text.replace(old, new)
path.write_text(text, encoding="utf-8")
