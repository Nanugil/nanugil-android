// 동승자 흐름 끝까지: QR 주소 → 로그인으로 → 가입 → 코드가 채워진 참여 → 이름 고르기 → 그룹 → 정산 요청 도착 → 송금 완료 표시
// 실제 Supabase가 필요하다 (.env.local, 마이그레이션 적용, Confirm email 끔).
import { expect, test } from "@playwright/test";
import { hasServer, newUser, prepareTrip, requestSettlement } from "../tests/helpers";

test.skip(!hasServer, ".env.local에 Supabase 키가 없음");

test("동승자가 가입하고 참여해서 정산 요청을 받아 보냈다고 표시한다", async ({ page }) => {
  const driver = await newUser("강운전");
  const trip = await prepareTrip(driver.sb, ["김민수"], { groupName: "E2E MT", destination: "청평호 펜션" });
  await driver.sb.rpc("set_pay_link", { p_group_id: trip.groupId, p_link: "https://qr.kakaopay.com/e2e" });

  await page.goto(`/join?code=${trip.code}`);
  await expect(page).toHaveURL(/\/login\?next=%2Fjoin%3Fcode%3D/);

  await page.getByRole("link", { name: "이메일로 회원가입" }).click();
  await page.waitForLoadState("networkidle"); // 하이드레이션 전에 입력하면 React가 값을 비운다
  await page.getByLabel("이메일").fill(`nanugil-e2e+${crypto.randomUUID()}@example.com`);
  await page.getByLabel("비밀번호", { exact: true }).fill("e2e-password-1");
  await page.getByLabel("이름", { exact: true }).fill("김민수");
  await page.getByRole("button", { name: "가입하기" }).click();

  await expect(page).toHaveURL(new RegExp(`/join\\?code=${trip.code}`));
  await expect(page.getByRole("region", { name: "E2E MT" })).toBeVisible();
  await page.getByText("김민수", { exact: true }).click();
  await expect(page.getByRole("radio", { name: "김민수" })).toBeChecked();
  await page.getByRole("button", { name: "참여하기" }).click();
  await expect(page).toHaveURL(new RegExp(`/groups/${trip.groupId}`));

  await page.waitForTimeout(2000); // 실시간 구독이 붙을 시간
  await requestSettlement(driver.sb, trip.tripId, 11750, [{ from: trip.passengerMemberIds[0], to: trip.driverMemberId, amountWon: 11750 }]);
  await expect(page).toHaveURL(new RegExp(`/trips/${trip.tripId}/settle`), { timeout: 10_000 });
  await expect(page.getByRole("heading", { name: "보낼 금액" })).toBeVisible();
  await expect(page.getByText("11,750원").first()).toBeVisible();
  await expect(page.getByRole("link", { name: "카카오페이 송금" })).toHaveAttribute("href", "https://qr.kakaopay.com/e2e");

  await page.getByRole("button", { name: "송금 완료 표시" }).click();
  await expect(page.getByText("송금 완료로 표시했습니다. 운전자의 확인을 기다리고 있습니다.")).toBeVisible();
});
