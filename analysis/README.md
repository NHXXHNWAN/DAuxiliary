# QQ 9.3.60 分析记录

该目录用于保存 QQ 版本分析材料。

当前仓库中的 QQ 防撤回实现基于 QQNT `IQQNTWrapperSession$CppProxy.onMsfPush`，并通过 Protobuf 结构识别撤回推送。
