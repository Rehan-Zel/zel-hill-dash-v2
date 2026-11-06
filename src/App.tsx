const files = [
  "settings.gradle.kts",
  "build.gradle.kts",
  "gradle.properties",
  "app/build.gradle.kts",
  "app/src/main/AndroidManifest.xml",
  "app/src/main/java/com/hilldash/game/MainActivity.kt",
  "app/src/main/java/com/hilldash/game/GameEngine.kt",
  ".github/workflows/build-apk.yml",
];

export default function App() {
  return (
    <main className="min-h-screen bg-slate-900 text-slate-100 flex items-center justify-center p-6">
      <div className="max-w-xl w-full rounded-2xl bg-slate-800 p-8 shadow-xl">
        <h1 className="text-3xl font-extrabold text-amber-400">Hill Dash</h1>
        <p className="mt-2 text-slate-300">
          Native Android (Kotlin + Jetpack Compose) project. Build the APK with the
          included GitHub Actions workflow.
        </p>
        <ul className="mt-6 space-y-1 font-mono text-sm text-emerald-300">
          {files.map((f) => (
            <li key={f}>• {f}</li>
          ))}
        </ul>
      </div>
    </main>
  );
}
