<head>
    <title>WLS Bestillingsbekreftelse</title>
    <style>
        dt {
            font-weight: bold;
        }
        dl, dd {
            font-size: 0.9rem;
        }
        dd {
            margin-bottom: 1em;
        }
        td {
            border: 2px solid;
        }
    </style>
</head>

<div>
    <p>
        Bestillingen din er ferdig prossesert.
    </p>
        <dl>
            <dt>Bestillingstype:</dt>
            <dd>${orderType}</dd>
            <dt>Melding fra bestiller:</dt>
            <dd><#if order.note??>${order.note}</#if></dd>
            <dt>Referansenummer:</dt>
            <dd>${order.hostOrderId}</dd>
        </dl>
    <h4>Ordrelinjer</h4>
    <table>
        <tr>
            <td>
                <b>Objekt-ID (Hyllesignatur)</b>
            </td>
            <td>
                <b>Status</b>
            </td>
        </tr>
        <#list orderLines as orderLine>
            <tr>
                <td>${orderLine.hostId}</td>
                <td>${orderLine.status}</td>
            </tr>
        </#list>
    </table>
</div>
