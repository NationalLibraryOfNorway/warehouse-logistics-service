package no.nb.mlt.wls.application.hostapi.item

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import no.nb.mlt.wls.domain.ports.inbound.AddNewItem
import no.nb.mlt.wls.domain.ports.inbound.GetItem
import no.nb.mlt.wls.domain.ports.inbound.MoveItem
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ServerWebInputException

@RestController
@RequestMapping(path = [ "/v1"])
@Tag(name = "Item Controller", description = "API for managing items in Hermes WLS")
class ItemController(
    private val addNewItem: AddNewItem,
    private val getItem: GetItem,
    private val moveItem: MoveItem
) {
    @Operation(
        summary = "Register an items in the storage system",
        description = """Register data about the item in Hermes WLS and appropriate storage system,
            so that the physical item can be placed in the physical storage.
            An item is also called item by some storage systems and users, those mean the same thing in Hermes.
            NOTE: When registering new item quantity and location are set to default values (0.0 and null).
            Hence you should not provide these values in the payload, or at least know they will be overwritten."""
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = """Item with given 'hostName' and 'hostId' already exists in the system.
                    No new item was created, neither was the old item updated.
                    Existing item information is returned for inspection.
                    In rare cases the response body may be empty, that can happen if Hermes WLS
                    does not have the information about the item stored in its database and
                    is unable to retrieve the existing item information from the storage system.""",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiItemPayload::class)
                    )
                ]
            ),
            ApiResponse(
                responseCode = "201",
                description = """Item payload is valid and the item information was registered successfully.
                    Item was created in the appropriate storage system.
                    New item information is returned for inspection.""",
                content = [
                    Content(
                        mediaType = "application/json",
                        schema = Schema(implementation = ApiItemPayload::class)
                    )
                ]
            ),
            ApiResponse(
                responseCode = "400",
                description = """Item payload is invalid and no new item was created.
                    Error message contains information about the invalid fields.""",
                content = [Content(schema = Schema())]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Client sending the request is not authorized to create a new item.",
                content = [Content(schema = Schema())]
            ),
            ApiResponse(
                responseCode = "403",
                description = "A valid 'Authorization' header is missing from the request.",
                content = [Content(schema = Schema())]
            )
        ]
    )
    @PostMapping("/item")
    suspend fun createItem(
        @RequestBody payload: ApiItemPayload
    ): ResponseEntity<ApiItemPayload> {
        throwIfInvalidPayload(payload)

        getItem.getItem(payload.hostName, payload.hostId)?.let {
            return ResponseEntity.ok(it.toApiPayload())
        }

        val item = addNewItem.addItem(payload.toItemMetadata())

        return ResponseEntity(item.toApiPayload(), HttpStatus.CREATED)
    }

    private fun throwIfInvalidPayload(payload: ApiItemPayload) {
        if (payload.hostId.isBlank()) {
            throw ServerWebInputException("The item's hostId is required, and it cannot be blank")
        }

        if (payload.description.isBlank()) {
            throw ServerWebInputException("The item's description is required, and it cannot be blank")
        }

        if (payload.itemCategory.isBlank()) {
            throw ServerWebInputException("The item's category is required, and it cannot be blank")
        }
    }
}
