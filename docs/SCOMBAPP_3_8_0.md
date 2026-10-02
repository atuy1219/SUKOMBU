# ScombApp 3.8.0の通信調査

[APIリファレンス](../SCOMBZ_API.md)を補足する、公式Androidアプリの静的解析記録です。SUKOMBUで呼び出さないAPIも参照対象に含めます。

## 解析対象

| 項目 | 値 |
| --- | --- |
| 調査日 | 2026-10-03（日本時間） |
| package | `jp.ac.shibaura_it.sic.scombmobile` |
| versionName / versionCode | `3.8.0` / `93` |
| Dart / ABI | `3.8.0` / `arm64-v8a` |
| 入力 | `base.apk`と`split_config.arm64_v8a.apk`を含むsplit APKのZIP |
| `base.apk` SHA-256 | `2aec49a5802c4d2cee3b6ea8d95b303c0ba205f57a13a43871fc6911fcf318df` |
| ARM64 split SHA-256 | `e502eea0e782d963e01514af64d94ccafcdacaf9beb7a94944768212fb09e85b` |
| `libapp.so` SHA-256 | `7ff819177f1218cd75151a00cca2fe93aebe1171544c458913378e18bc06aac1` |

APKや復元したアプリ全体のソースはリポジトリに含めません。リクエスト先・キー・処理の確認結果を記録します。サーバーへの試験リクエスト、ログイン、出席登録・打刻は実施していません。

## 範囲と確度

- `libapp.so`内のアプリ固有の通信処理とモデル、Android側の通信先定数を調べています。
- 「通信処理で確認」は呼び出し命令やJSONへの変換で確認した事項です。「文字列で確認」はバイナリに定数があることだけを示します。
- サーバーが受理する全パラメーター、必須条件、権限、現在の動作は静的解析では保証できません。アプリが知らないサーバー側APIも対象外です。
- HTTPメソッドごとに数えます。同じURLのGETとPOSTは別の操作です。

Mobile APIは13操作、教室推定は2操作を実行コードで確認しました。別途、GETによるシラバス検索と任意URLの添付ダウンロード処理があります。

## 通信処理の確認箇所

アドレスは上記`libapp.so`の関数開始オフセットです。呼び出し命令を示す場合は表内に注記します。バージョンが異なるバイナリにはそのまま適用できません。

| 操作 | 公式アプリ内の確認箇所 | オフセット |
| --- | --- | --- |
| POST `/login` | `LoginAPI.login` → `API.post` | `0x68f6e0` |
| POST `/reg_fcm` | `RegFcmApi.registerFCM` → `API.post` | `0x68f4bc` |
| POST `/unreg_fcm` | `setting_screen.dart`のログアウト処理 → 共通関数 → `API.post` | 呼び出し命令`0x69b89c`、共通関数`0x68f4bc` |
| GET `/sessionid` | `SetSessionIdAPI.getCurrentSessionId` → `API.get` | `0x6129f4` |
| POST `/sessionid` | `SetSessionIdAPI.setSessionId` → `API.post` | `0x6919cc` |
| GET `/otkey` | `OTKeyAPI.getOTKey` → `API.get` | `0x612388` |
| GET `/timetable/{yearMonth}` | `TimetableAPI.fetchTimetable` → `API.get` | `0x7cbe88` |
| POST `/timetable/{yearMonth}` | `TimetableAPI.updateCellInfo` → `API.post` | `0x7923c8` |
| GET `/home/{yearMonth}` | `HomeAPI.fetchHomeInfo` → `API.get` | `0x63e684` |
| GET `/task/{yearMonth}` | `TaskAPI.fetchAllTasks` → `API.get` | `0x7cca08` |
| GET `/news` | `NewsAPI.fetchAllNews` → `API.get` | `0x7cc214` |
| POST `/attend` | `AttendApi.postAttend` → `API.post` | `0x58b708` |
| GET `/attend/{classId}` | `AttendHistoryApi.getAttendHistory` → `API.get` | `0x6aadf0` |
| GET `/estimate_room/auth` | `DefaultApi.estimateRoomAuthGetWithHttpInfo` → `ApiClient.invokeAPI` | `0x5d72ac` |
| POST `/estimate_room/estimate` | `DefaultApi.estimateRoomEstimatePostWithHttpInfo` → `ApiClient.invokeAPI` | `0x5d6454` |

`API.get` / `API.post`は`package:scomb_mobile/common/api/api.dart`にあり、さらに`package:http/http.dart`のget / postを呼びます。教室推定の処理とモデルは`package:openapi/api.dart`です。Mobile APIの固定URLはオブジェクトプール、動的URLは各APIのコンストラクターで確認しています。

`/unreg_fcm`はオブジェクトプールの`pp+0x1c338`にURLがあります。`setting_screen.dart`ではURLを読む`0x69b864`、`UnregFcmApi`を生成する`0x69b878`、そのURLを`field_7`へ格納する`0x69b884`を経て、`0x69b89c`で`0x68f4bc`を呼びます。共通関数は`fcm_token`をJSON化して`API.post`へ渡します。

このビルドには`dedup_instructions`によるコード共有があり、共通関数が復元出力では`RegFcmApi.registerFCM`と表示されます。`UnregFcmApi`のクラス出力にメソッドがなくても、通信がないとは限りません。URL定数と呼び出し先だけでなく、APIインスタンスへ設定したURLまで照合する必要があります。

## 関連Web通信

### シラバス検索

`libapp.so`に次のURLテンプレートが含まれます。`syllabus_search_dialog.dart`の`fetchAllSyllabusSearchResult`（`0x677810`）からHTTP GET（`0x677864`）を呼び、レスポンスをEUC-JPからUTF-8へ変換してHTMLとして解析します。JSON APIではありません。

```text
http://syllabus.sic.shibaura-it.ac.jp/namazu/namazu.cgi?ajaxmode=true&query=${className}&whence=0&idxname=${admissionYearAndSection}&max=20&result=normal&sort=score
```

| パラメーター | テンプレート値・用途 |
| --- | --- |
| `ajaxmode` | `true` |
| `query` | 授業名 `className` |
| `whence` | `0` |
| `idxname` | `admissionYearAndSection`。具体的な値の構成は未確定 |
| `max` | `20` |
| `result` | `normal` |
| `sort` | `score` |

テンプレートの`idxname`は年度の変数と選択区分を`%2F`で結合して埋め込む処理があります。HTMLからリンクの表示テキストと`href`を取り出しています。検索条件のサーバー上の全仕様は未検証です。

### ScombZ画面

Mobile APIとは別のWeb画面です。APIリファレンスの「関連するWeb URL」も参照してください。

```text
https://scombz.shibaura-it.ac.jp/portal/home
https://scombz.shibaura-it.ac.jp/community/search
https://scombz.shibaura-it.ac.jp/news/{suffix}
https://scombz.shibaura-it.ac.jp/portal/surveys/take?surveyId={surveyId}
https://scombz.shibaura-it.ac.jp/saml/login?idp=http://adfs.sic.shibaura-it.ac.jp/adfs/services/trust
```

`{suffix}`は文字列結合で作られる部分を示す記号です。固定のAPIパスではありません。

### 添付ファイルのダウンロード

`webpage_screen.dart`の処理はダウンロード対象のURLにGETし、WebViewのCookieを取得できた場合に`Cookie`ヘッダーへ渡します（HTTP呼び出し`0x69e3ec`）。固定の新しいAPIパスではなく、Web画面が指定するURLの取得です。

## 外部SDKの通信先

Androidの`classes.dex`には次のGimbal SDK用URL定数も含まれます。ScombZのAPIではなく、定数の存在だけでは実行時に使われることを意味しません。HTTPメソッド、下位パス、内部の認証仕様は未調査です。

```text
https://analytics-server.gimbal.com/service/
https://communicate.gimbal.com/service/
https://eds.gimbal.com
https://order-api.gimbal.com
https://placebubble.gimbal.com/service/
https://registration.gimbal.com/service/
https://resolve.gimbal.com/
https://sdk-configuration.gimbal.com/service/
https://sdk-info.gimbal.com/service/
https://sightings.gimbal.com/
```

Firebase/GoogleやFlutterプラグインなど、汎用ライブラリ内部の全API仕様を列挙する資料ではありません。`scombapp/bluetooth`はFlutterとAndroidをつなぐローカルのチャネル名であり、HTTP APIではありません。

OpenAPI生成コードには、実際のAPI呼び出しが残っていないモデルもあります。以下に名前と`fromJson`で読み取るキーを残します。`DefaultApi`の復元可能な通信メソッドは教室推定の2操作であり、モデル名だけから管理用APIのURLや可用性を推定しません。

## 生成OpenAPIモデル一覧

`package:openapi/api.dart`に残る18モデルです。キーの有無とクライアントモデルの記録であり、全モデルを実際のレスポンスで受信したという意味ではありません。

| モデル | 読み取りキー |
| --- | --- |
| `UsersLoginPutRequest` | `email`, `password` |
| `UsersLoginPut201Response` | `token` |
| `UserRegistration` | `email`, `username`, `password` |
| `User` | `user_id`, `email`, `username`, `password`, `token`, `lastlogin` |
| `Room` | `basyo_cd`, `basyo_name_ja`, `basyo_name_en`, `basyo_tiku`, `basyo_last_upd`, `floor_id` |
| `ModelMap` | `map_id`, `floor_id`, `building_id`, `map_data` |
| `MapsRegisterPostRequest` | `floor_id`, `map_data` |
| `LogsGimbalActivityGimbalIdGet400Response` | `error` |
| `Log` | `log_id`, `ip`, `method`, `path`, `protocol`, `size`, `status`, `referrer`, `userAgent`, `created_at` |
| `Gimbal` | `gimbal_id`, `name`, `room_id`, `x`, `y`, `map_id`, `floor`, `building`, `room`, `battery`, `data` |
| `Floor` | `floor_id`, `name`, `building_id` |
| `EstimateRoomEstimatePostRequest` | `challenge_response`, `challenge_key`, `room`, `roomEnglish`, `gimbalRssis` |
| `EstimateRoomEstimatePost200Response` | `scomb_auth_otkey`, `timestamp`, `estimated_room_from_class`, `estimated_rooms` |
| `EstimateRoomAuthGet200Response` | `challenge`, `challenge_key` |
| `Building` | `building_id`, `name`, `campus` |
| `BeaconSighting` | `gimbal_id`, `rssis`, `time`, `batteryLevel` |
| `AnnouncementsRegisterPostRequest` | `title`, `content` |
| `Announcement` | `announcement_id`, `title`, `content`, `created_at`, `updated_at` |

`EstimateRoomEstimatePostRequest.toJson`（`0x5d4228`）で`gimbalRssis`などの送信キー、`BeaconSighting.toJson`（`0x5d45f8`）でその配列要素のキーを確認しています。出席登録の送信キーは`AttendApi.postAttend`内のJSON生成処理、出席履歴のキーは`AttendHistoryModel.fromJson`で確認しました。

`ClassCell.fromJson`の`basyo_cd`、`NewsItemModel.fromJson`の`obj_name1`～`obj_name3`・`file_name1`～`file_name3`は、SUKOMBUの既存資料や一部のモデル名と異なります。APIリファレンスは公式アプリのキーに訂正し、SUKOMBU本体のモデル変更は含めていません。

## 再確認の手順

1. split APKから`base.apk`とARM64 splitを取り出し、AndroidManifest.xmlのバージョンと上記ハッシュを確認する。
2. ARM64 splitの`lib/arm64-v8a/libapp.so`と`libflutter.so`を取り出す。
3. [Blutter](https://github.com/worawit/blutter)でDart AOTのオブジェクトと通信処理を解析する。
4. `package:scomb_mobile/common/api/`と`package:openapi/`のメソッド、リクエスト・レスポンスモデルを追い、メソッド・URL・JSONキーを照合する。
5. `base.apk`の`classes.dex`からアプリ固有の処理と外部SDK定数を分けて確認する。

静的なURL文字列だけでは、文字列結合で作られるパスやメソッドを網羅できません。認証情報、アプリ固有の秘密値、実ユーザーのレスポンスは調査記録に含めません。
