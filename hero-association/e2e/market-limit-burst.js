export async function marketLimitBurst(sendBurst) {
  let responses
  for (let attempt = 0; attempt < 3; attempt += 1) {
    // Clear the previous fixed window and start near the next second's beginning.
    await new Promise((resolve) => setTimeout(resolve, 1150 + (1000 - Date.now() % 1000)))
    responses = await sendBurst()
    // A burst split across windows can admit all six requests. Retry only that case;
    // the caller still requires exactly five admitted requests and one Envoy 429.
    if (!responses.every((response) => response.status() === 400)) return responses
  }
  return responses
}
