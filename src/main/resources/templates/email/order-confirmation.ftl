<div>
    Bestillingen din har blitt mottatt.
    <p>
        Type: ${order.orderType}
    </p>
    <p>
        Bestiller: ${order.contactPerson}
    </p>
    <p>
        Melding fra bestiller: ${order.note}
    </p>
    <p>
        Referansenummer: ${order.hostOrderId}
    </p>
    <h4>Ordrelinjer</h4>
    <ol>
        <#list order.orderLine as orderItem>
            <li>
                ${orderItem.hostId}
            </li>
        </#list>
    </ol>
</div>
