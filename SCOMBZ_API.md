# ScombZ Mobile API仕様

この文書は、SUKOMBUが扱うScombZ Mobile APIの非公式仕様です。サーバー側の変更により、予告なく利用できなくなる可能性があります。

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

| Method | Path | SUKOMBU | 用途 |
| --- | --- | --- | --- |
| POST | `/login` | 実装済み | ログインとBearerトークン取得 |
| POST | `/reg_fcm` | 実装済み | FCMトークン登録 |
| 未確定 | `/unreg_fcm` | 未実装 | FCMトークン登録解除 |
| POST | `/sessionid` | 定義済み | ScombZセッションID送信 |
| GET | `/otkey` | 実装済み | Web画面用OTKEY取得 |
| GET | `/timetable/{yearMonth}` | 実装済み | 時間割取得 |
| POST | `/timetable/{yearMonth}` | 実装済み | 授業メモ・色などの更新 |
| GET | `/home/{yearMonth}` | 定義済み | ホーム情報取得 |
| GET | `/task/{yearMonth}` | 実装済み | 課題・テスト・アンケート取得 |
| GET | `/news` | 実装済み | お知らせ取得 |
| POST | `/attend` | 未実装 | 出席登録 |
| 未確定 | `/attend/{suffix}` | 未実装 | 登下校・打刻履歴取得 |

`yearMonth`は年度と学期を表す6桁の値です。SUKOMBUでは前期を`YYYY01`、後期を`YYYY02`として扱います。

`/unreg_fcm`と`/attend/{suffix}`は、HTTPメソッド、パス末尾、完全なリクエスト形式が未確定です。

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

FCMトークン登録解除用のエンドポイントです。

```text
https://smob.sic.shibaura-it.ac.jp/smob/api/unreg_fcm
```

正確なHTTPメソッドとRequest bodyは未確定です。`/reg_fcm`と同じ`fcm_token`を使用する可能性があります。

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
    "basyoCD": "...",
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
| `basyoCD` | string | yes | 教室・場所コード |
| `quarter` | string / integer | yes | クォーター情報。型は未確定 |

一部の追加項目は、すべてのレスポンスで返るとは限りません。

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
    "dakoku_time": "...",
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
| `home_dakoku.dakoku_campus` | string | yes | 打刻キャンパス |

空データ時の表現と各配列要素の完全なnull許容性は未確定です。

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
    "objectName1": null,
    "objectName2": null,
    "objectName3": null,
    "fileName1": null,
    "fileName2": null,
    "fileName3": null,
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
| `objectName1`～`objectName3` | string | yes | 添付オブジェクト名 |
| `fileName1`～`fileName3` | string | yes | 添付ファイル名 |
| `otkey` | string | yes | Web画面用キー |

SUKOMBUの既読・未読切替はローカルRoomデータベース上で行います。現在のAPI定義には、既読状態を書き戻すエンドポイントはありません。

`archived`と`starred`はローカル状態として扱われる可能性があります。

## POST `/attend`

出席登録を行います。

### Request

```json
{
  "classId": "class-id",
  "seatId": "seat-id",
  "roomId": "room-id"
}
```

正確なキー表記、必須条件、追加項目、Response形式は未確定です。

## `/attend/{suffix}`

登下校・打刻履歴を取得します。パス末尾の値と完全なレスポンス構造は未確定です。

関連する項目：

```text
dakoku_kbn
dakoku_loc
dakoku_time
dakoku_campus
```

コード値の意味は未確定です。

## 教室推定API

ScombZ Mobile APIとは別に、次のBase URLを使用します。

```text
https://smobatnd.sic.shibaura-it.ac.jp/api
```

| Method | Path | 用途 |
| --- | --- | --- |
| GET | `/estimate_room/auth` | 教室推定用チャレンジ取得 |
| POST | `/estimate_room/estimate` | ビーコン情報などから教室を推定 |

関連するJSONキー：

```text
challenge
challenge_key
challenge_response
gimbal_rssis
seat_id
scomb_auth_otkey
estimated_rooms
estimated_room_from_class
```

チャレンジ計算方式と配列要素の完全な構造は未確定です。

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
