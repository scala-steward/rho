package com.http4s.rho.hal.plus.swagger.demo

import cats.Monad
import cats.syntax.functor._
import org.http4s.rho.Result.BaseResult
import org.http4s.rho.RhoService
import org.http4s.rho.hal.{ResourceObjectBuilder => ResObjBuilder, _}
import org.http4s.{Request, Uri}

import scala.collection.mutable.ListBuffer

class RestService[F[_]: Monad](val businessLayer: BusinessLayer) extends RhoService[F] {

  // # Query Parameters

  val firstResult = param[Int]("firstResult", 0, (i: Int) => i >= 0)
  val maxResults = param[Int]("maxResults", 10, (i: Int) => i >= 1 && i <= 100)
  val owner = param[String]("owner", "")
  val groups = param[Seq[String]]("groups", Nil)
  val searchTerm = param[String]("searchTerm", "")
  val sortBy = param[String]("sortBy", "")
  val showHidden = param[Int]("showHidden", 0)

  // # Path Variables

  val id = pathVar[Int]("id")

  // # HTTP Routes

  val browsers = "browsers"
  GET / browsers +? firstResult & maxResults |>> { (request: Request[F], first: Int, max: Int) =>
    val configurations = businessLayer.findBrowsers(first, max)
    val total = businessLayer.countBrowsers
    val hal = browsersAsResource(request, first, max, configurations, total)

    Ok(hal.build())
  }

  val browserById = browsers / id
  GET / browserById |>> { (request: Request[F], id: Int) =>
    val found = for { browser <- businessLayer.findBrowser(id) } yield {
      val b = browserAsResourceObject(browser, request)
      if (businessLayer.hasOperatingSystemsByBrowserId(browser.id))
        for (tpl <- operatingSystemsByBrowser.asUriTemplate(request))
          b.link("operating-systems", tpl.expandPath("id", browser.id).toUriIfPossible.get)

      Ok(b.build())
    }

    val res: F[BaseResult[F]] =
      (found getOrElse NotFound(warning(s"Browser $id not found"))).widen

    res
  }

  val browserPatternsById = browsers / id / "patterns"
  GET / browserPatternsById |>> { (request: Request[F], id: Int) =>
    val found = for { patterns <- businessLayer.findBrowserPatternsByBrowserId(id) }
      yield Ok(browserPatternsAsResource(request, 0, Int.MaxValue, patterns, patterns.size).build())

    val res: F[BaseResult[F]] =
      (found getOrElse NotFound(warning(s"Browser $id not found"))).widen

    res
  }

  val browserPatterns = "browser-patterns"
  GET / browserPatterns +? firstResult & maxResults |>> { (request: Request[F], first: Int, max: Int) =>
    val patterns = businessLayer.findBrowserPatterns(first, max)
    val total = businessLayer.countBrowsers
    val hal = browserPatternsAsResource(request, first, max, patterns, total)

    Ok(hal.build())
  }

  val browserPatternById = browserPatterns / id
  GET / browserPatternById |>> { (request: Request[F], id: Int) =>
    val found = for { pattern <- businessLayer.findBrowserPattern(id) } yield {
      val b = browserPatternAsResourceObject(pattern, request)
      for {
        tpl <- browserById.asUriTemplate(request)
        browserId <- businessLayer.findBrowserIdByPatternId(pattern.id)
      } b.link("browser", tpl.expandPath("id", browserId).toUriIfPossible.get)

      Ok(b.build())
    }

    val res: F[BaseResult[F]] =
      (found getOrElse NotFound(warning(s"Browser $id not found"))).widen

    res
  }

  val browserTypes = "browser-types"
  GET / browserTypes |>> { (request: Request[F]) =>
    val types = businessLayer.findBrowserTypes
    val hal = browserTypesAsResource(request, types)

    Ok(hal.build())
  }

  val browserTypeById = browserTypes / id
  GET / browserTypeById |>> { (request: Request[F], id: Int) =>
    val found = for { browserType <- businessLayer.findBrowserType(id) } yield {
      val b = browserTypeAsResourceObject(browserType, request)
      for {
        tpl <- browsersByBrowserTypeId.asUriTemplate(request)
      } b.link("browsers", tpl.expandPath("id", browserType.id).toUriIfPossible.get)

      Ok(b.build())
    }

    val res: F[BaseResult[F]] =
      (found getOrElse NotFound(warning(s"Browser type $id not found"))).widen

    res
  }

  val browsersByBrowserTypeId = browserTypes / id / "browsers"
  GET / browsersByBrowserTypeId +? firstResult & maxResults |>> { (request: Request[F], id: Int, first: Int, max: Int) =>
    val browsers = businessLayer.findBrowsersByBrowserTypeId(id, first, max)
    val total = businessLayer.countBrowsersByBrowserTypeId(id)

    val res: F[BaseResult[F]] = if (browsers.nonEmpty)
      Ok(browsersAsResource(request, first, max, browsers, total).build()).widen
    else
      NotFound(warning(s"No browsers for type $id found")).widen

    res
  }

  val operatingSystems = "operating-systems"
  GET / operatingSystems +? firstResult & maxResults |>> { (request: Request[F], first: Int, max: Int) =>
    val configurations = businessLayer.findOperatingSystems(first, max)
    val total = businessLayer.countOperatingSystems
    val hal = operatingSystemsAsResource(request, first, max, configurations, total)

    Ok(hal.build())
  }

  val operatingSystemById = operatingSystems / id
  GET / operatingSystemById |>> { (request: Request[F], id: Int) =>
    val found = for { operatingSystem <- businessLayer.findOperatingSystem(id) } yield {
      val b = operatingSystemAsResourceObject(operatingSystem, request)
      if (businessLayer.hasBrowsersByOperatingSystemId(operatingSystem.id))
        for (tpl <- browsersByOperatingSystem.asUriTemplate(request))
          b.link("browsers", tpl.expandPath("id", operatingSystem.id).toUriIfPossible.get)

      Ok(b.build())
    }

    val res: F[BaseResult[F]] =
      (found getOrElse NotFound(warning(s"OperatingSystem $id not found"))).widen

    res
  }

  val browsersByOperatingSystem = operatingSystemById / "browsers"
  GET / browsersByOperatingSystem |>> { (request: Request[F], id: Int) =>
    val browsers = businessLayer.findBrowsersByOperatingSystemId(id)

    val res: F[BaseResult[F]] = if (browsers.nonEmpty)
      Ok(browsersAsResource(request, 0, Int.MaxValue, browsers, browsers.size).build()).widen
    else
      NotFound(warning(s"No Browsers for operating system $id found")).widen

    res
  }

  val operatingSystemsByBrowser = browserById / "operating-systems"
  GET / operatingSystemsByBrowser |>> { (request: Request[F], id: Int) =>
    val operatingSystems = businessLayer.findOperatingSystemsByBrowserId(id)

    val res: F[BaseResult[F]] = if (operatingSystems.nonEmpty)
      Ok(operatingSystemsAsResource(request, 0, Int.MaxValue, operatingSystems, operatingSystems.size).build()).widen
    else
      NotFound(warning(s"No operating systems for browser $id found")).widen

    res
  }

  GET / "" |>> { request: Request[F] =>
    val b = new ResObjBuilder[Nothing, Nothing]()
    b.link("self", request.uri)
    for (uri <- browsers.asUri(request)) b.link(browsers, uri.toString, "Lists browsers")
    for (uri <- browserPatterns.asUri(request)) b.link(browserPatterns, uri.toString, "Lists browser patterns")
    for (uri <- browserTypes.asUri(request)) b.link(browserTypes, uri.toString, "Lists browser types")
    for (uri <- operatingSystems.asUri(request)) b.link(operatingSystems, uri.toString, "Lists operating systems")

    Ok(b.build())
  }

  // # JSON HAL helpers

  def browsersAsResource(request: Request[F], first: Int, max: Int, browsers: Seq[Browser], total: Int): ResObjBuilder[(String, Long), Browser] = {
    val self = request.uri
    val hal = new ResObjBuilder[(String, Long), Browser]()
    hal.link("self", selfWithFirstAndMax(self, first, max))
    hal.content("total", total)

    if (first + max < total) {
      hal.link("next", self +? (firstResult, first + max) +? (maxResults, max))
    }
    if (first > 0) {
      hal.link("prev", self +? (firstResult, Math.max(first - max, 0)) +? (maxResults, max))
    }
    val res = ListBuffer[ResourceObject[Browser, Nothing]]()
    browsers.foreach { browser =>
      res.append(browserAsResourceObject(browser, request).build())
    }
    hal.resources("browsers", res.toList)
  }

  def browserAsResourceObject(browser: Browser, request: Request[F]): ResObjBuilder[Browser, Nothing] = {
    val b = new ResObjBuilder[Browser, Nothing]()
    for (tpl <- browserById.asUriTemplate(request))
      b.link("self", tpl.expandPath("id", browser.id).toUriIfPossible.get)
    for (tpl <- browserPatternsById.asUriTemplate(request))
      b.link("patterns", tpl.expandPath("id", browser.id).toUriIfPossible.get)
    for (tpl <- browserTypeById.asUriTemplate(request))
      b.link("type", tpl.expandPath("id", browser.typeId).toUriIfPossible.get)

    b.content(browser)
  }

  def browserPatternsAsResource(request: Request[F], first: Int, max: Int, browserPatterns: Seq[BrowserPattern], total: Int): ResObjBuilder[(String, Long), BrowserPattern] = {
    val self = request.uri
    val hal = new ResObjBuilder[(String, Long), BrowserPattern]()
    hal.link("self", selfWithFirstAndMax(self, first, max))
    hal.content("total", total)
    if (first + max < total) {
      hal.link("next", self +? (firstResult, first + max) +? (maxResults, max))
    }
    if (first > 0) {
      hal.link("prev", self +? (firstResult, Math.max(first - max, 0)) +? (maxResults, max))
    }
    val res = ListBuffer[ResourceObject[BrowserPattern, Nothing]]()
    browserPatterns.foreach { browserPattern =>
      res.append(browserPatternAsResourceObject(browserPattern, request).build())
    }
    hal.resources("browserPatterns", res.toList)
  }

  def browserPatternAsResourceObject(browserPattern: BrowserPattern, request: Request[F]): ResObjBuilder[BrowserPattern, Nothing] = {
    val b = new ResObjBuilder[BrowserPattern, Nothing]()
    for (tpl <- browserPatternById.asUriTemplate(request))
      b.link("self", tpl.expandPath("id", browserPattern.id).toUriIfPossible.get)
    b.content(browserPattern)
  }

  def browserTypeAsResourceObject(browserType: BrowserType, request: Request[F]): ResObjBuilder[BrowserType, Nothing] = {
    val b = new ResObjBuilder[BrowserType, Nothing]()
    for (tpl <- browserTypeById.asUriTemplate(request))
      b.link("self", tpl.expandPath("id", browserType.id).toUriIfPossible.get)
    b.content(browserType)
  }

  def browserTypesAsResource(request: Request[F], browserTypes: Seq[BrowserType]): ResObjBuilder[Nothing, BrowserType] = {
    val self = request.uri
    val hal = new ResObjBuilder[Nothing, BrowserType]()
    hal.link("self", self)
    val res = ListBuffer[ResourceObject[BrowserType, Nothing]]()
    browserTypes.foreach { browserType =>
      res.append(browserTypeAsResourceObject(browserType, request).build())
    }
    hal.resources("browserTypes", res.toList)
  }

  def operatingSystemsAsResource(request: Request[F], first: Int, max: Int, operatingSystems: Seq[OperatingSystem], total: Int): ResObjBuilder[(String, Long), OperatingSystem] = {
    val self = request.uri
    val hal = new ResObjBuilder[(String, Long), OperatingSystem]()
    hal.link("self", selfWithFirstAndMax(self, first, max))
    hal.content("total", total)
    if (first + max < total) {
      hal.link("next", self +? (firstResult, first + max) +? (maxResults, max))
    }
    if (first > 0) {
      hal.link("prev", self +? (firstResult, Math.max(first - max, 0)) +? (maxResults, max))
    }
    val res = ListBuffer[ResourceObject[OperatingSystem, Nothing]]()
    operatingSystems.foreach { operatingSystem =>
      res.append(operatingSystemAsResourceObject(operatingSystem, request).build())
    }
    hal.resources("operatingSystems", res.toList)
  }

  def operatingSystemAsResourceObject(operatingSystem: OperatingSystem, request: Request[F]): ResObjBuilder[OperatingSystem, Nothing] = {
    val b = new ResObjBuilder[OperatingSystem, Nothing]()
    for (tpl <- operatingSystemById.asUriTemplate(request))
      b.link("self", tpl.expandPath("id", operatingSystem.id).toUriIfPossible.get)
    b.content(operatingSystem)
  }

  def selfWithFirstAndMax(self: Uri, first: Int, max: Int): Uri = {
    if (!self.containsQueryParam(firstResult) && !self.containsQueryParam(maxResults)) self
    else self +? (firstResult, first) +? (maxResults, max)
  }

  // use JSON messages if a non-successful HTTP status must be send

  def message(text: String, `type`: MessageType): Message = {
    Message(text, `type`)
  }
  def error(text: String): Message = message(text, Error)
  def info(text: String):  Message = message(text, Info)
  def warning(text: String):  Message = message(text, Warning)

}
