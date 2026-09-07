# Your First Worker

## Craft the controller

The Automatone Controller is reusable and does not bind itself to a worker. Its
current holder always opens their own roster.

```text
Iron Ingot   Copper Ingot  Iron Ingot
Iron Ingot   Glass Pane    Iron Ingot
             Redstone
```

Use the controller while holding it to open **Overview**.

## Deploy and start

1. Open **Batch jobs**.
2. Search for blocks by translated name or registry ID and select the targets.
3. Choose a quantity for each worker, or choose **Unlimited**.
4. Add one new worker and select a real tool from your inventory.
5. Keep or change the material list. The default is 64 ordinary cobblestone for
   building stock; all supplies must exist in your inventory.
6. Review the preview. A shortage blocks deployment and consumes nothing.
7. Choose **Deploy & start**. **Deploy only** creates an equipped idle worker.

Destination preparation runs asynchronously. Closing the controller does not cancel
it. If you disconnect before deployment finishes, completed workers remain and
unfinished deployments are cancelled without consuming their unused supplies.

Return to **Overview** to see the worker's location, targets, progress, state, and
problems. Open its card for Job, Inventory, and Settings controls.
