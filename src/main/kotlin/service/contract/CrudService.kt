package org.burgas.service.contract

import org.burgas.dto.Request
import org.burgas.dto.Response
import java.util.UUID

interface CrudService<R : Request, S : Response, F : Response> {

    suspend fun findById(id: UUID): F

    suspend fun findAll(): List<S>

    suspend fun create(request: R): F

    suspend fun update(request: R): F

    suspend fun delete(id: UUID)
}