package space.chunks.lobby.extensions

import chunks.space.api.explorer.chunk.v1alpha1.Types

fun List<Types.FlavorVersion>.latestCompleted(): Types.FlavorVersion? =
    this.filter { it.buildStatus == Types.BuildStatus.COMPLETED }
        .maxWithOrNull(compareBy({ it.createdAt.seconds }, { it.createdAt.nanos }))
