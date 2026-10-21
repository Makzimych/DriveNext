// ЛР2. Преобразование ответа Яндекс ID в поля пользователя для Supabase.
// Supabase ожидает стандартные поля пользователя; Яндекс возвращает почту в default_email.
// Секретный ключ service_role не используется. Токен доступа проверяется запросом к Яндексу.
Deno.serve(async (request: Request) => {
  if (request.method !== "GET") return new Response("Method not allowed", { status: 405 });
  const header = request.headers.get("authorization") ?? "";
  const token = header.match(/^Bearer ([^\s]+)$/i)?.[1];
  if (!token) return new Response("Unauthorized", { status: 401 });
  try {
    const upstream = await fetch("https://login.yandex.ru/info?format=json", {
      headers: { Authorization: `OAuth ${token}` },
      signal: AbortSignal.timeout(10_000),
    });
    if (!upstream.ok) return new Response("Unauthorized", { status: 401 });
    const user = await upstream.json();
    if (typeof user.id !== "string" || typeof user.default_email !== "string") {
      return new Response("Email permission is required", { status: 422 });
    }
    return Response.json({
      sub: user.id,
      email: user.default_email,
      email_verified: false,
      name: user.real_name ?? user.display_name ?? "",
      given_name: user.first_name ?? "",
      family_name: user.last_name ?? "",
    }, { headers: { "Cache-Control": "no-store" } });
  } catch {
    return new Response("Identity provider is temporarily unavailable", { status: 502 });
  }
});
