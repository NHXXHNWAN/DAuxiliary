# QQ 9.3.60

- 基线版本：QQ 9.3.60
- 目标回调：`com.tencent.qqnt.kernel.nativeinterface.IQQNTWrapperSession$CppProxy.onMsfPush`
- 推送命令：`trpc.msg.olpush.OlPushService.MsgPush`
- 解析路径：`MsgPush -> Message -> ContentHead`
- C2C 撤回：`type=528, subType=138`
- 群聊撤回：`type=732, subType=17`

## 限制

以上目标与字段仍需 QQ + LSPosed 真机验证；解析失败、签名不匹配或候选不明确时保持 QQ 原始行为。
