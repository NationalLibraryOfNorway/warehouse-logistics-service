package no.nb.mlt.wls

import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/hermes/test")
class TestController {

  @GetMapping("/open", produces = [APPLICATION_JSON_VALUE])
  fun open(): ResponseEntity<String> {
    return ResponseEntity.ok("Hello to an open endpoint!")
  }

  @GetMapping("/authenticated", produces = [APPLICATION_JSON_VALUE])
  fun authenticated(authentication: Authentication): ResponseEntity<String> {
    return ResponseEntity.ok("Hello ${authentication.name} to an authenticated endpoint!")
  }

  @GetMapping("/authorized", produces = [APPLICATION_JSON_VALUE])
  fun authorized(authentication: Authentication): ResponseEntity<String> {
    return ResponseEntity.ok("Hello ${authentication.name} to an endpoint that only users with the 'wls-dev-role' authority can access! You have: ${authentication.authorities.joinToString(", ")}")
  }
}
