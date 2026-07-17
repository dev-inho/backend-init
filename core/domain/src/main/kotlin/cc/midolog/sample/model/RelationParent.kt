package cc.midolog.sample.model

data class RelationParent(
    val id: String,
    val name: String,
    val children: List<RelationChild> = emptyList(),
)
