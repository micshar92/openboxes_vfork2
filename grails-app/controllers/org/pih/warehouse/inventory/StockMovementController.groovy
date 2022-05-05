/**
 * Copyright (c) 2012 Partners In Health.  All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 **/

package org.pih.warehouse.inventory

import grails.converters.JSON
import grails.validation.ValidationException
import org.grails.plugins.csv.CSVWriter
import org.pih.warehouse.api.StockMovement
import org.pih.warehouse.api.StockMovementItem
import org.pih.warehouse.api.StockMovementType
import org.pih.warehouse.core.ActivityCode
import org.pih.warehouse.core.BulkDocumentCommand
import org.pih.warehouse.core.Constants
import org.pih.warehouse.core.Document
import org.pih.warehouse.core.DocumentCommand
import org.pih.warehouse.core.DocumentType
import org.pih.warehouse.core.Event
import org.pih.warehouse.core.Location
import org.pih.warehouse.core.User
import org.pih.warehouse.importer.ImportDataCommand
import org.pih.warehouse.order.OrderTypeCode
import org.pih.warehouse.picklist.PicklistItem
import org.pih.warehouse.requisition.Requisition
import org.pih.warehouse.requisition.RequisitionSourceType
import org.pih.warehouse.requisition.RequisitionStatus
import org.pih.warehouse.shipping.Shipment
import org.pih.warehouse.shipping.ShipmentStatusCode

class StockMovementController {

    def dataService
    def reportService
    def productService
    def shipmentService
    def stockMovementService

    // This template is generated by webpack during application start
    def index = {
        redirect(action: "create", params: params)
    }

    def create = {
        StockMovementType stockMovementType = params.direction as StockMovementType
        if (stockMovementType == StockMovementType.INBOUND) {
            redirect(action: "createInbound")
        }
        else {
            redirect(action: "createOutbound")
        }
    }

    def createOutbound = {
        render(template: "/common/react", params: params)
    }

    def createInbound = {
        render(template: "/common/react", params: params)
    }

    def createRequest = {
        render(template: "/common/react", params: params)
    }

    def verifyRequest = {
        render(template: "/common/react", params: params)
    }

    def createCombinedShipments = {
        render(template: "/common/react", params: params)
    }

    def edit = {
        Location currentLocation = Location.get(session.warehouse.id)
        StockMovement stockMovement = params.id ? stockMovementService.getStockMovement(params.id) : null

        if(!stockMovement.isEditAuthorized(currentLocation)) {
            flash.error = stockMovementService.getDisabledMessage(stockMovement, currentLocation, true)
            redirect(controller: "stockMovement", action: "show", id: params.id)
            return
        }

        StockMovementType stockMovementType = currentLocation == stockMovement.origin ?
                StockMovementType.OUTBOUND : currentLocation == stockMovement.destination || stockMovement?.origin?.isSupplier() ?
                        StockMovementType.INBOUND : null

        if (stockMovementType == StockMovementType.OUTBOUND && stockMovement.requisition.sourceType == RequisitionSourceType.ELECTRONIC) {
            redirect(action: "verifyRequest", params: params)
        }
        else if (stockMovementType == StockMovementType.INBOUND) {
            if (stockMovement.isFromOrder) {
                redirect(action: "createCombinedShipments", params: params)
            } else if (stockMovement.requisition.sourceType == RequisitionSourceType.ELECTRONIC) {
                if (stockMovement.requisition?.status == RequisitionStatus.CREATED) {
                    redirect(action: "createRequest", params: params)
                } else {
                    redirect(action: "verifyRequest", params: params)
                }
            } else {
                redirect(action: "createInbound", params: params)
            }
        }
        else {
            if (stockMovement.isFromOrder) {
                redirect(action: "createCombinedShipments", params: params)
            }
            redirect(action: "createOutbound", params: params)
        }
    }

    def show = {
        StockMovement stockMovement = stockMovementService.getStockMovement(params.id)
        [stockMovement: stockMovement]
    }

    def validatePicklist = {
        StockMovement stockMovement = stockMovementService.getStockMovement(params.id)

        try {
            if (stockMovementService.validatePicklist(params.id)) {
                flash.message = "Validated"
            }
        } catch (ValidationException e) {
            flash.errors = e.errors
        }

        redirect(action: "show", id: params.id)
    }



    def list = {

        def max = params.max ? params.max as int : 10
        def offset = params.offset ? params.offset as int : 0
        Date dateCreatedFrom = params.dateCreatedFrom ? Date.parse("MM/dd/yyyy", params.dateCreatedFrom) : null
        Date dateCreatedTo = params.dateCreatedTo ? Date.parse("MM/dd/yyyy", params.dateCreatedTo) : null
        Date requestedDeliveryDateFrom = params.requestedDeliveryDateFrom ? Date.parse("MM/dd/yyyy", params.requestedDeliveryDateFrom) : null
        Date requestedDeliveryDateTo = params.requestedDeliveryDateTo ? Date.parse("MM/dd/yyyy", params.requestedDeliveryDateTo) : null
        Date expectedDeliveryDateFrom = params.expectedDeliveryDateFrom ? Date.parse("MM/dd/yyyy", params.expectedDeliveryDateFrom) : null
        Date expectedDeliveryDateTo = params.expectedDeliveryDateTo ? Date.parse("MM/dd/yyyy", params.expectedDeliveryDateTo) : null
        Date expectedShippingDateFrom = params.expectedShippingDateFrom ? Date.parse("MM/dd/yyyy", params.expectedShippingDateFrom) : null
        Date expectedShippingDateTo = params.expectedShippingDateTo ? Date.parse("MM/dd/yyyy", params.expectedShippingDateTo) : null
        Location currentLocation = Location.get(session?.warehouse?.id)

        StockMovementType stockMovementType = params.direction ? params.direction as StockMovementType : null
        // On initial request we set the origin and destination based on the direction
        if (stockMovementType == StockMovementType.OUTBOUND) {
            params.origin = params.origin ?: currentLocation
            params.destination = params.destination ?: null
        } else if (stockMovementType == StockMovementType.INBOUND) {
            params.origin = params.origin ?: null
            params.destination = params.destination ?: currentLocation
        } else {
            // This is necessary because sometimes we need to infer the direction from the parameters
            if (params.origin?.id == currentLocation?.id && params.destination?.id == currentLocation?.id) {
                stockMovementType = null
                params.direction = null
            } else if (params.origin?.id == currentLocation?.id) {
                stockMovementType = StockMovementType.OUTBOUND
                params.direction = stockMovementType.toString()
            } else if (params.destination?.id == currentLocation?.id) {
                stockMovementType = StockMovementType.INBOUND
                params.direction = stockMovementType.toString()
            } else {
                params.origin = params.origin ?: currentLocation
                params.destination = params.destination ?: currentLocation
            }
        }

        if (params.format) {
            max = null
            offset = null
        }

        // Discard the requisition so it does not get saved at the end of the request
        Requisition requisition = new Requisition(params)
        requisition.discard()

        // Create stock movement to be used as search criteria
        StockMovement stockMovement = new StockMovement()
        if (params.q) {
            stockMovement.name = "%" + params.q + "%"
            stockMovement.identifier = "%" + params.q + "%"
            stockMovement.description = "%" + params.q + "%"
            stockMovement.trackingNumber = "%" + params.q + "%"
        }

        stockMovement.stockMovementType = stockMovementType
        stockMovement.requestedBy = requisition.requestedBy
        stockMovement.createdBy = requisition.createdBy
        stockMovement.origin = requisition.origin
        stockMovement.destination = requisition.destination
        stockMovement.statusCode = requisition?.status ? requisition?.status.toString() : null
        stockMovement.receiptStatusCodes = params.receiptStatusCode ? params?.list("receiptStatusCode") as ShipmentStatusCode[] : null
        stockMovement.requisitionStatusCodes = params.status ? params?.list("status") as RequisitionStatus[] : null
        stockMovement.requestType = requisition?.type
        stockMovement.sourceType = requisition?.sourceType
        stockMovement.updatedBy = requisition?.updatedBy

        def stockMovements

        try {
            stockMovements = stockMovementService.getStockMovements(stockMovement,
                    [
                            max: max,
                            offset: offset,
                            sort: params.sort,
                            order: params.order,
                            productSearch: params.q,
                            dateCreatedFrom: dateCreatedFrom,
                            dateCreatedTo: dateCreatedTo,
                            expectedShippingDateFrom: expectedShippingDateFrom,
                            expectedShippingDateTo: expectedShippingDateTo,
                            expectedDeliveryDateFrom: expectedDeliveryDateFrom,
                            expectedDeliveryDateTo: expectedDeliveryDateTo,
                            requestedDeliveryDateFrom: requestedDeliveryDateFrom,
                            requestedDeliveryDateTo: requestedDeliveryDateTo
                    ]
            )
        } catch(Exception e) {
            flash.message = "${e.message}"
        }

        if (params.format && stockMovements) {

            def sw = new StringWriter()
            def csv = new CSVWriter(sw, {
                "Status" { it.status }
                "Receipt Status" { it.receiptStatus }
                "Identifier" { it.id }
                "Name" { it.name }
                "Origin" { it.origin }
                "Destination" { it.destination }
                "Stocklist" { it.stocklist }
                "Requested by" { it.requestedBy }
                "Date Requested" { it.dateRequested }
                "Date Created" { it.dateCreated }
                "Date Shipped" { it.dateShipepd }
            })

            stockMovements.each { stockMov ->
                csv << [
                        status       : stockMov.status,
                        receiptStatus: stockMov.shipment?.status,
                        id           : stockMov.identifier,
                        name         : stockMov.description,
                        origin       : stockMov.origin?.name ?: "",
                        destination  : stockMov.destination?.name ?: "",
                        stocklist    : stockMov.stocklist?.name ?: "",
                        requestedBy  : stockMov.requestedBy ?: warehouse.message(code: 'default.none.label'),
                        dateRequested: stockMov.dateRequested.format("MM-dd-yyyy") ?: "",
                        dateCreated  : stockMov.requisition?.dateCreated?.format("MM-dd-yyyy") ?: "",
                        dateShipepd  : stockMov.shipment?.expectedShippingDate?.format("MM-dd-yyyy") ?: "",
                ]
            }

            response.setHeader("Content-disposition", "attachment; filename=\"StockMovements-${new Date().format("yyyyMMdd-hhmmss")}.csv\"")
            render(contentType: "text/csv", text: sw.toString(), encoding: "UTF-8")
        }

        if (params.submitted) {
            flash.message = "${warehouse.message(code:'request.submitMessage.label')} ${params.movementNumber}"
        }

        render(view: "list", params: params, model: [stockMovements: stockMovements])
    }

    def rollback = {
        Location currentLocation = Location.get(session.warehouse.id)
        StockMovement stockMovement = stockMovementService.getStockMovement(params.id)
        if (stockMovement.isDeleteOrRollbackAuthorized(currentLocation) ||
                (stockMovement.isFromOrder && currentLocation?.supports(ActivityCode.ENABLE_CENTRAL_PURCHASING))) {
            try {
                stockMovementService.rollbackStockMovement(params.id)
                flash.message = "Successfully rolled back stock movement with ID ${params.id}"
            } catch (Exception e) {
                log.error("Unable to rollback stock movement with ID ${params.id}: " + e.message, e)
                flash.message = "Unable to rollback stock movement with ID ${params.id}: " + e.message
            }
        } else {
            flash.error = "You are not able to rollback shipment from your location."
        }

        redirect(action: "show", id: params.id)
    }

    def synchronizeDialog = {
        StockMovement stockMovement = stockMovementService.getStockMovement(params.id)
        boolean isAllowed = stockMovementService.isSynchronizationAuthorized(stockMovement)

        def data = stockMovement?.requisition?.picklist?.picklistItems.collect { PicklistItem picklistItem ->
            def expirationDate = picklistItem?.inventoryItem?.expirationDate ?
                    Constants.EXPIRATION_DATE_FORMATTER.format(picklistItem?.inventoryItem?.expirationDate) : null
            return [
                    productCode: picklistItem?.requisitionItem?.product?.productCode,
                    productName: picklistItem?.requisitionItem?.product?.name,
                    binLocation: picklistItem?.binLocation?.name,
                    lotNumber: picklistItem?.inventoryItem?.lotNumber,
                    expirationDate: expirationDate,
                    status: picklistItem?.requisitionItem?.status,
                    requested: picklistItem?.requisitionItem?.quantity,
                    picked: picklistItem?.quantityPicked,
                    pickReasonCode: picklistItem?.reasonCode,
                    editReasonCode: picklistItem?.requisitionItem?.cancelReasonCode
            ]
        }

        render(template: "synchronizeDialog", model: [stockMovement: stockMovement, data: data, isAllowed:isAllowed])
    }

    def synchronize = {
        log.info "params " + params
        StockMovement stockMovement = stockMovementService.getStockMovement(params.id)
        Date dateShipped = params.dateShipped as Date
        if (stockMovementService.isSynchronizationAuthorized(stockMovement)) {
            try {
                stockMovementService.synchronizeStockMovement(params.id, dateShipped)
                flash.message = "Successfully synchronized stock movement with ID ${params.id}"
            } catch (Exception e) {
                log.error("Unable to synchronize stock movement with ID ${params.id}: " + e.message, e)
                flash.message = "Unable to synchronize stock movement with ID ${params.id}: " + e.message
            }
        } else {
            flash.error = "You are not authorized to synchronize this stock movement."
        }

        redirect(action: "show", id: params.id)
    }

    def remove = {
        Location currentLocation = Location.get(session.warehouse.id)
        boolean isCentralPurchasingEnabled = currentLocation?.supports(ActivityCode.ENABLE_CENTRAL_PURCHASING)
        StockMovement stockMovement = stockMovementService.getStockMovement(params.id)
        if (stockMovement.isDeleteOrRollbackAuthorized(currentLocation)) {
            if (stockMovement?.shipment?.currentStatus == ShipmentStatusCode.PENDING || !stockMovement?.shipment?.currentStatus) {
                try {
                    stockMovementService.deleteStockMovement(params.id)
                    flash.message = "Successfully deleted stock movement with ID ${params.id}"
                } catch (Exception e) {
                    log.error("Unable to delete stock movement with ID ${params.id}: " + e.message, e)
                    flash.message = "Unable to delete stock movement with ID ${params.id}: " + e.message
                }
            } else {
                flash.message = "You cannot delete a shipment with status ${stockMovement?.shipment?.currentStatus}"
            }
        }
        else {
            flash.message = "You are not able to delete stock movement from your location."
            if (params.show) {
                redirect(action: "show", id: params.id)
                return
            }
        }
        // We need to set the correct parameter so stock movement list is displayed properly
        params.direction = (currentLocation == stockMovement.origin) ? StockMovementType.OUTBOUND :
                (currentLocation == stockMovement.destination) ? StockMovementType.INBOUND : "ALL"

        if (isCentralPurchasingEnabled) {
            redirect(controller: 'order', action: "list", params: [orderTypeCode: OrderTypeCode.PURCHASE_ORDER])
            return
        }
        redirect(action: "list", params:params)
    }

    def requisition = {
        StockMovement stockMovement = stockMovementService.getStockMovement(params.id)
        render(template: "requisition", model: [stockMovement: stockMovement])
    }

    def lineItems = {
        StockMovement stockMovement = stockMovementService.getStockMovement(params.id)
        render(template: "lineItems", model: [stockMovement: stockMovement])
    }

    def schedule = {
        StockMovement stockMovement = stockMovementService.getStockMovement(params.id)
        render(template: "schedule", model: [stockMovement: stockMovement])
    }

    def events = {
        StockMovement stockMovement = stockMovementService.getStockMovement(params.id)
        render(template: "events", model: [stockMovement: stockMovement])
    }

    def documents = {
        StockMovement stockMovement = stockMovementService.getStockMovement(params.id)
        render(template: "documents", model: [stockMovement: stockMovement])
    }

    def packingList = {
        StockMovement stockMovement = stockMovementService.getStockMovement(params.id)
        render(template: "packingList", model: [stockMovement: stockMovement])
    }

    def receipts = {
        StockMovement stockMovement = stockMovementService.getStockMovement(params.id)
        def receiptItems = stockMovementService.getStockMovementReceiptItems(stockMovement)
        render(template: "receipts", model: [receiptItems: receiptItems])
    }

    def saveSchedule = {
        log.info "save schedule " + params
        StockMovement stockMovement = stockMovementService.getStockMovement(params.id)

        Requisition requisition = stockMovement.requisition
        if (requisition) {
            requisition.requestedDeliveryDate = params.requestedDeliveryDate
            requisition.save()
        }

        Shipment shipment = stockMovement.shipment
        if (shipment) {
            shipment.expectedDeliveryDate = params.expectedDeliveryDate
            shipment.expectedShippingDate = params.expectedShippingDate

            if (params?.receivingLocation?.id) {
                shipment.setReceivingScheduled(Location.load(params.receivingLocation.id), new Date(), User.load(session.user.id))
            }
            else {
                shipment.removeEvent(shipment?.receivingScheduled)
            }

            if (params?.packingLocation?.id) {
                shipment.setPackingScheduled(Location.load(params.packingLocation.id), new Date(), User.load(session.user.id))
            }
            else {
                shipment.removeEvent(shipment?.packingScheduled)
            }

            if (params?.loadingLocation?.id) {
                shipment.setLoadingScheduled(Location.load(params.loadingLocation.id), new Date(), User.load(session.user.id))
            }
            else {
                shipment.removeEvent(shipment?.loadingScheduled)
            }
            shipment.save()
        }

        flash.message = "Saved scheduling information"

        redirect(action: "show", id: stockMovement.id)
    }

    def saveEvent = {

        StockMovement stockMovement = stockMovementService.getStockMovement(params.id)
        def shipmentInstance = stockMovement.shipment

        def eventInstance = Event.get(params.eventId) ?: new Event()
        bindData(eventInstance, params)

        // check for errors
        if (eventInstance?.validate() && eventInstance.hasErrors()) {
            flash.message = "${warehouse.message(code: 'shipping.unableToEditEvent.message', args: [format.metadata(obj: eventInstance?.eventType)])}"
        }
        else {
            eventInstance.save(flush: true)
            shipmentInstance.addToEvents(eventInstance)
            shipmentInstance.save(flush: true)
        }
        redirect(action: 'show', id: shipmentInstance.id)
    }

    def uploadDocument = { DocumentCommand command ->
        StockMovement stockMovement = stockMovementService.getStockMovement(params.id)

        Shipment shipment = stockMovement.shipment
        Document document = new Document()
        document.fileContents = command.fileContents.bytes
        document.contentType = command.fileContents.fileItem.contentType
        document.name = command.fileContents.fileItem.name
        document.filename = command.fileContents.fileItem.name
        document.documentType = DocumentType.get(Constants.DEFAULT_DOCUMENT_TYPE_ID)

        shipment.addToDocuments(document)
        shipment.save()

        render([data: "Document was uploaded successfully"] as JSON)
    }


    def uploadDocuments = { BulkDocumentCommand command ->
        StockMovement stockMovement = stockMovementService.getStockMovement(params.id)
        Shipment shipment = stockMovement.shipment

        command.filesContents.each { fileContent ->
            Document document = new Document()
            document.fileContents = fileContent.bytes
            document.contentType = fileContent.fileItem.contentType
            document.name = fileContent.fileItem.name
            document.filename = fileContent.fileItem.name
            document.documentType = DocumentType.get(Constants.DEFAULT_DOCUMENT_TYPE_ID)

            shipment.addToDocuments(document)
        }
        shipment.save()

        render([data: "Documents were uploaded successfully"] as JSON)
    }

    def addDocument = {
        log.info params
        StockMovement stockMovement = stockMovementService.getStockMovement(params.id)

        Shipment shipmentInstance = stockMovement.shipment
        def documentInstance = Document.get(params?.document?.id)
        if (!documentInstance) {
            documentInstance = new Document()
        }
        if (!shipmentInstance) {
            flash.message = "${warehouse.message(code: 'default.not.found.message', args: [warehouse.message(code: 'shipment.label', default: 'Shipment'), params.id])}"
            redirect(action: "list")
        }
        render(view: "addDocument", model: [shipmentInstance: shipmentInstance, documentInstance: documentInstance])
    }

    def exportCsv = {
        StockMovement stockMovement = stockMovementService.getStockMovement(params.id)
        List lineItems = stockMovementService.buildStockMovementItemList(stockMovement)
        String csv = dataService.generateCsv(lineItems)
        response.setHeader("Content-disposition", "attachment; filename=\"StockMovementItems-${params.id}.csv\"")
        render(contentType: "text/csv", text: csv.toString(), encoding: "UTF-8")
    }


    def importCsv = { ImportDataCommand command ->

        try {
            StockMovement stockMovement = stockMovementService.getStockMovement(params.id)

            def importFile = command.importFile
            if (importFile.isEmpty()) {
                throw new IllegalArgumentException("File cannot be empty")
            }

            if (importFile.fileItem.contentType != "text/csv") {
                throw new IllegalArgumentException("File must be in CSV format")
            }

            String csv = new String(importFile.bytes)
            def settings = [separatorChar: ',', skipLines: 1]
            Integer sortOrder = 0
            csv.toCsvReader(settings).eachLine { tokens ->

                StockMovementItem stockMovementItem = StockMovementItem.createFromTokens(tokens)
                stockMovementItem.stockMovement = stockMovement
                stockMovementItem.sortOrder = sortOrder
                stockMovement.lineItems.add(stockMovementItem)
                sortOrder += 100
            }
            stockMovementService.updateItems(stockMovement)

        } catch (Exception e) {
            // FIXME The global error handler does not return JSON for multipart uploads
            log.warn("Error occurred while importing CSV: " + e.message, e)
            response.status = 500
            render([errorCode: 500, errorMessage: e?.message ?: "An unknown error occurred during import"] as JSON)
            return
        }

        render([data: "Data will be imported successfully"] as JSON)
    }

    def exportItems = {
        def shipmentItems = []
        def shipments = shipmentService.getShipmentsByDestination(session.warehouse)

        shipments.findAll {
            it.currentStatus == ShipmentStatusCode.SHIPPED || it.currentStatus == ShipmentStatusCode.PARTIALLY_RECEIVED
        }.each { shipment ->
            shipment.shipmentItems.findAll { it.quantityRemaining > 0 }.groupBy {
                it.product
            }.each { product, value ->
                shipmentItems << [
                        productCode         : product.productCode,
                        productName         : product.name,
                        quantity            : value.sum { it.quantityRemaining },
                        expectedShippingDate: formatDate(date: shipment.expectedShippingDate, format: "dd-MMM-yy"),
                        shipmentNumber      : shipment.shipmentNumber,
                        shipmentName        : shipment.name,
                        origin              : shipment.origin,
                        destination         : shipment.destination,
                ]
            }
        }


        if (shipmentItems) {
            def date = new Date()
            def sw = new StringWriter()

            def csv = new CSVWriter(sw, {
                "Code" { it.productCode }
                "Product Name" { it.productName }
                "Quantity Incoming" { it.quantity }
                "Expected Shipping Date" { it.expectedShippingDate }
                "Shipment Number" { it.shipmentNumber }
                "Shipment Name" { it.shipmentName }
                "Origin" { it.origin }
                "Destination" { it.destination }
            })

            shipmentItems.each { shipmentItem ->
                csv << [
                        productCode         : shipmentItem.productCode,
                        productName         : shipmentItem.productName,
                        quantity            : shipmentItem.quantity,
                        expectedShippingDate: shipmentItem.expectedShippingDate,
                        shipmentNumber      : shipmentItem.shipmentNumber,
                        shipmentName        : shipmentItem.shipmentName,
                        origin              : shipmentItem.origin,
                        destination         : shipmentItem.destination,
                ]
            }
            response.contentType = "text/csv"
            response.setHeader("Content-disposition", "attachment; filename=\"Items shipped not received_${session.warehouse.name}_${date.format("yyyyMMdd-hhmmss")}.csv\"")
            render(contentType: "text/csv", text: csv.writer.toString())
            return
        } else {
            render(text: 'No shipments found', status: 404)
        }
    }

    def exportPendingRequisitionItems = {
        Location currentLocation = Location.get(session?.warehouse?.id)

        def pendingRequisitionItems = stockMovementService.getPendingRequisitionItems(currentLocation)

        def sw = new StringWriter()
        def csv = new CSVWriter(sw, {
            "Shipment Number" { it.shipmentNumber }
            "Description" { it.description }
            "Destination" { it.destination }
            "Status" { it.status }
            "Product Code" { it.productCode }
            "Product" { it.productName }
            "Qty Picked" { it.quantityPicked }
        })
        pendingRequisitionItems.each { requisitionItem ->
            def quantityPicked = requisitionItem?.totalQuantityPicked()
            if (quantityPicked) {
                csv << [
                        shipmentNumber  : requisitionItem?.requisition?.requestNumber,
                        description     : requisitionItem?.requisition?.description ?: '',
                        destination     : requisitionItem?.requisition?.destination,
                        status          : requisitionItem?.requisition?.status,
                        productCode     : requisitionItem?.product?.productCode,
                        productName     : requisitionItem?.product?.name,
                        quantityPicked  : quantityPicked,
                ]
            }
        }

        response.setHeader("Content-disposition", "attachment; filename=\"PendingShipmentItems-${new Date().format("yyyyMMdd-hhmmss")}.csv\"")
        render(contentType: "text/csv", text: sw.toString(), encoding: "UTF-8")

    }

    def printPackingList = {
        StockMovement stockMovement = stockMovementService.getStockMovement(params.id)
        [stockMovement: stockMovement]
    }


}

