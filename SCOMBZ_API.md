# ScombZ Mobile API仕様

この文書は、ScombZ Mobile APIの非公式リファレンスです。SUKOMBUで未使用の通信も含め、公式AndroidアプリScombApp 3.8.0（versionCode 93）の静的解析で確認した内容を記録します。解析対象・再確認の手順・関連Web通信・モデル一覧は[調査記録](docs/SCOMBAPP_3_8_0.md)を参照してください。

大学が正式に公開・サポートするAPI仕様ではありません。アプリ内で確認できるAPIとサーバー側の全APIは同一ではなく、サーバーの必須条件・権限・現在の動作は未検証です。JSON例は説明用であり、実際の利用者のレスポンスではありません。サーバー側の変更により、予告なく利用できなくなる可能性があります。

認証情報、Bearerトークン、セッションID、OTKEY、FCMトークンをログやIssueへ投稿しないでください。

## 基本情報

| 項目 | 値 |
| --- | --- |
| Base URL | `https://smob.sic.shibaura-it.ac.jp/smob/api/` |
| データ形式 | JSON |
| SUKOMBUのHTTPクライアント | Retrofit / OkHttp |
| 認証 | Bearerトークン |

`POST /login`以外のリクエストでは、保存済みトークンが存在する場合に次のヘッダーを付与します。

```http
Authorization: Bearer <token>
```

SUKOMBUではHTTP 401をセッション失効として扱い、保存済み認証トークンを削除します。400番台はクライアントエラー、500番台はサーバーエラーとして処理します。

## エンドポイント一覧

| Method | Path | SUKOMBU | 用途 | 3.8.0での根拠 |
| --- | --- | --- | --- | --- |
| POST | `/login` | 実装済み | ログインとBearerトークン取得 | `LoginAPI.login` → `API.post` |
| POST | `/reg_fcm` | 実装済み | FCMトークン登録 | `RegFcmApi.registerFCM` → `API.post` |
| 未確定 | `/unreg_fcm` | 未実装 | FCMトークン登録解除の候補 | URL定数と`UnregFcmApi`クラスのみ |
| GET | `/sessionid` | 未実装 | 保存されたWebセッションID取得 | `SetSessionIdAPI.getCurrentSessionId` → `API.get` |
| POST | `/sessionid` | 定義済み | ScombZセッションID送信 | `SetSessionIdAPI.setSessionId` → `API.post` |
| GET | `/otkey` | 実装済み | Web画面用OTKEY取得 | `OTKeyAPI.getOTKey` → `API.get` |
| GET | `/timetable/{yearMonth}` | 実装済み | 時間割取得 | `TimetableAPI.fetchTimetable` → `API.get` |
| POST | `/timetable/{yearMonth}` | 実装済み | 授業メモ・色などの更新 | `TimetableAPI.updateCellInfo` → `API.post` |
| GET | `/home/{yearMonth}` | 定義済み | ホーム情報取得 | `HomeAPI.fetchHomeInfo` → `API.get` |
| GET | `/task/{yearMonth}` | 実装済み | 課題・テスト・アンケート取得 | `TaskAPI.fetchAllTasks` → `API.get` |
| GET | `/news` | 実装済み | お知らせ取得 | `NewsAPI.fetchAllNews` → `API.get` |
| POST | `/attend` | 未実装 | 出席登録 | `AttendApi.postAttend` → `API.post` |
| GET | `/attend/{classId}` | 未実装 | 授業の出席履歴取得 | `AttendHistoryApi.getAttendHistory` → `API.get` |

`yearMonth`は年度と学期を表す6桁の値です。SUKOMBUでは前期を`YYYY01`、後期を`YYYY02`として扱います。

Mobile APIは12操作を通信処理で確認しました。`/unreg_fcm`は別に残す未確定候補です。教室推定APIの2操作は別ホストを使用します。

レスポンス表のnull許容性はクライアントモデル・処理の許容範囲であり、サーバー契約ではありません。公式アプリで確認した追加キーとSUKOMBUのモデルを区別して記載します。

## POST `/login`

### Request

```json
{
  "user": "USER_ID",
  "pass": "password"
}
```

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `user` | string | yes | 学籍番号・ユーザーID |
| `pass` | string | yes | パスワード |

### Response

```json
{
  "status": "OK",
  "user_type": "student",
  "gakubu": "...",
  "gakka": "...",
  "token": "...",
  "features": { "attend_test": false },
  "terms": [
    {
      "year": 2026,
      "start": ["...", "..."]
    }
  ]
}
```

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| `status` | string | no | 成否。SUKOMBUは`OK`を成功として扱う |
| `user_type` | string | yes | ユーザー種別 |
| `gakubu` | string | yes | 学部情報 |
| `gakka` | string | yes | 学科情報 |
| `token` | string | yes | Bearerトークン |
| `features` | object | yes | 公式アプリで読み取る機能フラグ。SUKOMBU未使用 |
| `features.attend_test` | boolean相当 | yes | 公式アプリの出席機能フラグ。サーバー上の意味・権限は未検証 |
| `terms` | array | yes | 学期情報 |
| `terms[].year` | integer | no | 年度 |
| `terms[].start` | string[] | no | 学期開始情報。各要素の意味は未確定 |

## POST `/reg_fcm`

### Request

```json
{
  "fcm_token": "firebase-registration-token"
}
```

### Response

```json
{
  "status": "OK"
}
```

## `/unreg_fcm`

FCMトークン登録解除用と考えられるURL定数です。実際に呼び出される通信処理は確認できません。

```text
https://smob.sic.shibaura-it.ac.jp/smob/api/unreg_fcm
```

オブジェクトプールにURL定数、復元クラス一覧に`UnregFcmApi extends API`が存在します。ただし、このビルドには登録解除メソッドの復元可能な実行コードがなく、HTTPメソッドとRequest bodyは確認できません。`/reg_fcm`から類推してPOSTや`fcm_token`と断定しません。

## GET `/sessionid`

Bearerトークンを付けて、保存済みのScombZ WebセッションIDを取得します。公式アプリは`SetSessionIdAPI.getCurrentSessionId`でレスポンスの`sessionid`を読み、nullまたは空文字ならセッションなしとして扱います。

### Response（読み取るキー）

```json
{
  "sessionid": "session-id"
}
```

`sessionid`はstring / nullです。`status`の有無など、読み取らないキーは未確定です。SUKOMBUにはこのGETメソッドの定義がありません。

## POST `/sessionid`

### Request

```json
{
  "sessionid": "session-id"
}
```

### Response

```json
{
  "status": "OK"
}
```

ScombZ WebセッションのIDをAPIへ送信します。現行SUKOMBUではRetrofitメソッドのみ定義され、通常のRepository処理からは呼び出していません。

## GET `/otkey`

### Response

```json
{
  "status": "OK",
  "otkey": "..."
}
```

`otkey`は授業、課題、テスト、アンケートなどのWeb画面を開く際に使用します。

## GET `/timetable/{yearMonth}`

### Response

```json
[
  {
    "classId": "123456",
    "name": "授業名",
    "nameEnglish": "Class name",
    "room": "教室",
    "roomEnglish": "Room",
    "teachers": "担当教員",
    "teachersEnglish": "Teacher",
    "period": 1,
    "dayOfWeek": 1,
    "syllabusUrl": "https://...",
    "numberOfCredit": 2,
    "note": "Base64 encoded value",
    "customColor": "4294967295",
    "otkey": "...",
    "basyo_cd": "...",
    "customizedNumberOfCredit": 2,
    "quarter": "..."
  }
]
```

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| `classId` | string | no | 授業ID |
| `name` | string | no | 授業名 |
| `nameEnglish` | string | yes | 授業名の英語表記 |
| `room` | string | yes | 教室 |
| `roomEnglish` | string | yes | 教室の英語表記 |
| `teachers` | string | no | 担当教員 |
| `teachersEnglish` | string | yes | 担当教員の英語表記 |
| `period` | integer | no | 時限。APIは1始まり、SUKOMBU内部は0始まり |
| `dayOfWeek` | integer | no | 曜日。APIは1始まり、SUKOMBU内部は0始まり |
| `syllabusUrl` | string | yes | シラバスURL |
| `numberOfCredit` | integer | yes | 単位数 |
| `note` | string | yes | Base64文字列 |
| `customColor` | string | yes | 符号なし整数を文字列化した色値 |
| `otkey` | string | yes | Web画面用キー |
| `basyo_cd` | string | yes | 公式アプリが読み取る教室・場所コード。`basyoCD`ではない |
| `customizedNumberOfCredit` | integer相当 | yes | 公式アプリが読み取るユーザー設定の単位数 |
| `quarter` | string / integer | yes | クォーター情報。型は未確定 |

一部の追加項目は、すべてのレスポンスで返るとは限りません。

`otkey`はSUKOMBUのモデルにある項目です。3.8.0の`ClassCell.fromJson`では読み取りを確認していません。

## POST `/timetable/{yearMonth}`

授業のメモや色を更新します。Request bodyは配列です。

```json
[
  {
    "classId": "123456",
    "customizedNumberOfCredit": 0,
    "note": "memo",
    "customColor": "4294967295"
  }
]
```

### Response

```json
{
  "status": "OK"
}
```

## GET `/home/{yearMonth}`

時間割、課題、お知らせ、打刻情報をまとめて取得します。

### Response

```json
{
  "home_timetable": [],
  "home_task": [],
  "home_news": [],
  "home_dakoku": {
    "dakoku_time": "09:00",
    "dakoku_kbn": "...",
    "dakoku_loc": "...",
    "dakoku_campus": "..."
  }
}
```

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| `home_timetable` | array | yes | 直近の時間割・授業情報 |
| `home_task` | array | yes | 課題・テスト・アンケート |
| `home_news` | array | yes | お知らせ |
| `home_dakoku` | object | yes | 登下校・打刻情報 |
| `home_dakoku.dakoku_time` | string | yes | 打刻時刻 |
| `home_dakoku.dakoku_kbn` | string | yes | 打刻区分。コード値の意味は未確定 |
| `home_dakoku.dakoku_loc` | string | yes | 打刻場所 |
| `home_dakoku.dakoku_campus` | string | yes | 打刻キャンパス |

空データ時の表現と各配列要素の完全なnull許容性は未確定です。

公式アプリの`DakokuInfoModel.fromJson`は`dakoku_time`を`HH:mm`で解析します。授業の出席履歴APIとは別の情報です。

## GET `/task/{yearMonth}`

### Response

```json
[
  {
    "taskType": 0,
    "id": "task-id",
    "classId": "class-id",
    "from": "授業名",
    "title": "Base64 encoded title",
    "done": 0,
    "allowLate": 0,
    "submitTimeFrom": "2026-07-01 00:00:00",
    "submitTimeTo": "2026-07-31 23:59:59",
    "publishTimeFrom": "2026-07-01 00:00:00",
    "publishTimeTo": "2026-08-01 00:00:00",
    "url": "...",
    "relatedClassId": "...",
    "otkey": "..."
  }
]
```

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| `taskType` | integer | yes | `0`: 課題、`1`: テスト、`2`: アンケート |
| `id` | string | yes | 課題等のID |
| `classId` | string | yes | 授業ID |
| `from` | string | yes | 授業名・発信元 |
| `title` | string | yes | Base64文字列 |
| `done` | integer | yes | `1`の場合は完了済み |
| `allowLate` | integer / boolean | yes | 遅延提出可否。型は未確定 |
| `submitTimeFrom` | string | yes | 提出開始日時 |
| `submitTimeTo` | string | yes | 締切日時 |
| `publishTimeFrom` | string | yes | 公開開始日時 |
| `publishTimeTo` | string | yes | 公開終了日時 |
| `url` | string | yes | 対象画面URL |
| `relatedClassId` | string | yes | 関連授業ID |
| `otkey` | string | yes | Web画面用キー |

日時文字列は通常`yyyy-MM-dd HH:mm:ss`形式です。

`url`・`relatedClassId`・`otkey`はSUKOMBUのモデルにある項目です。3.8.0の`Task.fromJson`では、これらをレスポンスから読み取る処理を確認していません。Web画面のURLは授業ID・課題IDなどから組み立てています。

## GET `/news`

### Response

```json
[
  {
    "newsId": "news-id",
    "classId": "class-id",
    "title": "Base64 encoded title",
    "author": "Base64 encoded author",
    "publishTime": "2026-07-23 12:00:00",
    "tags": "LMS,重要",
    "tagsEnglish": "LMS,Important",
    "readTime": null,
    "obj_name1": null,
    "obj_name2": null,
    "obj_name3": null,
    "file_name1": null,
    "file_name2": null,
    "file_name3": null,
    "otkey": "..."
  }
]
```

| Field | Type | Nullable | Description |
| --- | --- | --- | --- |
| `newsId` | string | no | お知らせID |
| `classId` | string | yes | LMSお知らせに関連する授業ID |
| `title` | string | yes | Base64文字列 |
| `author` | string | yes | 教授名または発信部署。Base64文字列 |
| `publishTime` | string | yes | 公開日時 |
| `tags` | string | yes | カンマ区切り。先頭要素をカテゴリとして使用 |
| `tagsEnglish` | string | yes | タグの英語表記 |
| `readTime` | string | yes | 空またはnullなら未読 |
| `obj_name1`～`obj_name3` | string | yes | 公式アプリが読み取る添付オブジェクト名 |
| `file_name1`～`file_name3` | string | yes | 公式アプリが読み取る添付ファイル名 |
| `otkey` | string | yes | Web画面用キー |

SUKOMBUの既読・未読切替はローカルRoomデータベース上で行います。現在のAPI定義には、既読状態を書き戻すエンドポイントはありません。

`archived`と`starred`はローカル状態として扱われる可能性があります。

SUKOMBUの現行モデルは添付キーを`objectName1`～`objectName3`、`fileName1`～`fileName3`と定義しています。上表は公式アプリがJSONから読むキーの記録であり、モデルの互換性修正はこの資料更新に含めません。`otkey`もSUKOMBUモデルの項目で、公式アプリの`NewsItemModel.fromJson`では読み取りを確認していません。

## POST `/attend`

出席登録を行います。公式アプリの`AttendApi.postAttend`で、次の5キーをJSON化して`API.post`へ渡す処理を確認しました。以前の資料の`classId`・`seatId`・`roomId`という例はこのビルドのリクエストと一致しません。

### Request

```json
{
  "student_id": "USER_ID",
  "class_id": "class-id",
  "seat_id": "seat-id",
  "time": "2026-10-03T09:00:00",
  "hash": "verification-hash"
}
```

| Field | 公式アプリの送信値 | 説明 |
| --- | --- | --- |
| `student_id` | string / null | 学生ID |
| `class_id` | string / null | 授業ID |
| `seat_id` | string / null | 座席ID |
| `time` | string | `yyyy-MM-dd'T'HH:mm:ss`で時刻を文字列化 |
| `hash` | string | 検証用ハッシュ。MD5処理を確認 |

ハッシュ生成では`ssHHmmMMddyyyy`形式の時刻、学生ID、追加の文字列を結合し、UTF-8化した後にMD5を計算しています。追加文字列の運用仕様やサーバー側の検証条件は未確認です。正しい出席登録を再現できることを保証するサンプルではありません。

公式アプリは返り値を個別のレスポンスモデルへ変換しません。サーバーの必須条件・成功レスポンスの全キーは未確定です。

## GET `/attend/{classId}`

授業の出席履歴を取得します。`AttendHistoryApi`は`/attend/`に引数を結合し、出席確認画面は選択した`ClassCell.classId`を渡しています。以前の資料の「登下校・打刻履歴」という説明を訂正します。登下校情報は`GET /home/{yearMonth}`の`home_dakoku`と区別してください。

### Response（公式アプリが読み取る構造）

```json
[
  {
    "dakoku_kbn": "...",
    "date": "2026-10-03 09:00:00",
    "class_id": "class-id",
    "kyositu_cd": "room-code"
  }
]
```

| Field | 公式モデルの入力型 | 説明 |
| --- | --- | --- |
| `dakoku_kbn` | string | 打刻・出席区分。コードの意味は未確定 |
| `date` | string | 出席日時 |
| `class_id` | string | 授業ID |
| `kyositu_cd` | string | 教室コード |

公式アプリは`date`を`yyyy-MM-dd hh:mm:ss`（小文字の`hh`）で解析しています。API側の時刻仕様が12時間表記であると断定する根拠ではありません。

## 教室推定API

ScombZ Mobile APIとは別に、次のBase URLを使用します。

```text
https://smobatnd.sic.shibaura-it.ac.jp/api
```

| Method | Path | SUKOMBU | 用途 |
| --- | --- | --- | --- |
| GET | `/estimate_room/auth` | 未実装 | 教室推定用チャレンジ取得 |
| POST | `/estimate_room/estimate` | 未実装 | ビーコン情報などから教室を推定 |

公式アプリの生成OpenAPIクライアント`DefaultApi`がメソッドとURLを渡す処理で確認しました。Mobile APIのBearer認証を、この別ホストにもそのまま適用できるとは限りません。認証の全条件は未確認です。

### GET `/estimate_room/auth`

クライアントは次のレスポンスモデルを読みます。

```json
{
  "challenge": "...",
  "challenge_key": "..."
}
```

両キーともstringです。チャレンジ応答の生成に必要な追加条件は未確定です。

### POST `/estimate_room/estimate`

Requestのシリアライズ処理で確認したキーは`challenge_response`・`challenge_key`・`room`・`roomEnglish`・`gimbalRssis`です。`gimbal_rssis`ではありません。`seat_id`はこのリクエストモデルにはありません。

```json
{
  "challenge_response": "...",
  "challenge_key": "...",
  "room": "教室名",
  "roomEnglish": "Room name",
  "gimbalRssis": [
    {
      "gimbal_id": "beacon-id",
      "rssis": ["-60", "-63"],
      "time": "...",
      "batteryLevel": 90
    }
  ]
}
```

| Field | 公式クライアントのモデル | 備考 |
| --- | --- | --- |
| `challenge_response` | string | チャレンジ応答 |
| `challenge_key` | string | authレスポンスのキー |
| `room` / `roomEnglish` | string / null | 教室情報。モデルはnullの場合にキーを省略 |
| `gimbalRssis` | `BeaconSighting[]` | ビーコン観測情報 |
| `gimbalRssis[].gimbal_id` | string | ビーコンID |
| `gimbalRssis[].rssis` | string[] | 公式モデルの型。サーバーが数値配列も受理するかは未検証 |
| `gimbalRssis[].time` | string | 観測時刻。文字列形式は未確定 |
| `gimbalRssis[].batteryLevel` | integer / null | モデルはnullの場合にキーを省略 |

Responseの読み取りキー：

```json
{
  "scomb_auth_otkey": "...",
  "timestamp": "...",
  "estimated_room_from_class": null,
  "estimated_rooms": []
}
```

| Field | 公式クライアントのモデル |
| --- | --- |
| `scomb_auth_otkey` | string / null |
| `timestamp` | string / null |
| `estimated_room_from_class` | `Room` / null |
| `estimated_rooms` | `Room[]` |

`Room`は`basyo_cd`・`basyo_name_ja`・`basyo_name_en`・`basyo_tiku`・`basyo_last_upd`・`floor_id`を読み取ります。これらはクライアントモデルの構造であり、サーバーが常にすべて返すことを保証しません。

## シラバス検索

公式アプリには、Mobile APIとは別の`GET http://syllabus.sic.shibaura-it.ac.jp/namazu/namazu.cgi`もあります。`ajaxmode=true`を付けていますが、返り値はHTMLとして解析しています。URLテンプレートと引数は[調査記録のシラバス検索](docs/SCOMBAPP_3_8_0.md#シラバス検索)を参照してください。

## Base64処理

SUKOMBUは次の値をBase64としてデコードし、失敗した場合は元の文字列をそのまま利用します。

- 課題の`title`
- お知らせの`title`
- お知らせの`author`
- 時間割の`note`

デコード後はUTF-8文字列として扱います。

## 関連するWeb URL

```text
https://mobile.scombz.shibaura-it.ac.jp/{otkey}/lms/course?idnumber={classId}
https://mobile.scombz.shibaura-it.ac.jp/{otkey}/lms/course/report/submission?idnumber={classId}&reportId={id}
https://mobile.scombz.shibaura-it.ac.jp/{otkey}/lms/course/examination/taketop?idnumber={classId}&examinationId={id}
https://mobile.scombz.shibaura-it.ac.jp/{otkey}/lms/course/surveys/take?idnumber={classId}&surveyId={id}
https://mobile.scombz.shibaura-it.ac.jp/news/{yearMonth}{newsId}?
https://scombz.shibaura-it.ac.jp/lms/course?idnumber={classId}
https://scombz.shibaura-it.ac.jp/lms/course/report/submission?idnumber={classId}&reportId={reportId}
https://scombz.shibaura-it.ac.jp/lms/course/examination/taketop?idnumber={classId}&examinationId={examinationId}
```

これらはAPIエンドポイントではなく、認証済みWeb画面です。
