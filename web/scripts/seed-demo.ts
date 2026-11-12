// 시연 데이터: README 정산 예시(운전자 2명 + 동승자 6명, 1인 11,750원)대로 그룹·운행을 만든다.
// 사용: npm run seed            그룹·운행·도착까지
//       npm run seed -- --settle  정산 요청까지 올린다
// 운전자 계정 이메일·비밀번호는 콘솔에만 찍는다.
import { newUser, prepareTrip, requestSettlement } from "../tests/helpers";

const PASSENGERS_A = ["김민수", "이서준", "박지우"];
const PASSENGERS_B = ["최하은", "정도윤", "한예린"];

async function main() {
  const settle = process.argv.includes("--settle");
  const driverA = await newUser("강운전");
  const t = await prepareTrip(driverA.sb, [...PASSENGERS_A, ...PASSENGERS_B], {
    groupName: "2026 신소재공학과 MT", destination: "청평호 펜션", driverName: "강운전", arrive: false,
  });
  // 두 번째 운전자는 차량 앱 대신 여기서 직접 참여·등록한다
  const driverB = await newUser("윤운전");
  const joined = await driverB.sb.rpc("join_group", { p_code: t.code, p_display_name: "윤운전" });
  if (joined.error || joined.data?.[0]?.error) throw new Error(`join_group: ${joined.error?.message ?? joined.data[0].error}`);
  const bRow = await driverB.sb.rpc("join_trip", { p_trip_id: t.tripId, p_vehicle_label: "2호차", p_fuel_type: "GASOLINE" });
  if (bRow.error) throw new Error(`join_trip: ${bRow.error.message}`);
  const driverBMemberId: string = bRow.data.member_id;
  const [a1, a2, a3, b1, b2, b3] = t.passengerMemberIds;
  await driverA.sb.rpc("set_trip_passengers", { p_trip_id: t.tripId, p_member_ids: [a1, a2, a3] });
  await driverB.sb.rpc("set_trip_passengers", { p_trip_id: t.tripId, p_member_ids: [b1, b2, b3] });
  await driverA.sb.rpc("mark_arrived", { p_trip_id: t.tripId });
  await driverB.sb.rpc("mark_arrived", { p_trip_id: t.tripId });
  await driverA.sb.rpc("set_pay_link", { p_group_id: t.groupId, p_link: "https://qr.kakaopay.com/demo-a" });
  await driverB.sb.rpc("set_pay_link", { p_group_id: t.groupId, p_link: "https://qr.kakaopay.com/demo-b" });

  if (settle) {
    // README: A 31,000원, B 63,000원, 8명 → 1인 11,750원. 동승자는 자기 차 운전자에게, A는 동승자에게 35,250원을 받아 B에게 16,000원을 넘긴다 (A 잔액 +19,250, B +51,250).
    const share = 11750;
    await requestSettlement(driverA.sb, t.tripId, share, [
      ...[a1, a2, a3].map((p) => ({ from: p, to: t.driverMemberId, amountWon: share })),
      ...[b1, b2, b3].map((p) => ({ from: p, to: driverBMemberId, amountWon: share })),
      { from: t.driverMemberId, to: driverBMemberId, amountWon: 16000 },
    ]);
  }

  console.log(`초대 코드: ${t.code}`);
  console.log(`그룹: ${t.groupId}  운행: ${t.tripId}`);
  console.log(`운전자 A: ${driverA.email} / ${driverA.password}`);
  console.log(`운전자 B: ${driverB.email} / ${driverB.password}`);
  console.log(settle ? "정산 요청까지 올렸어요" : "정산 요청은 아직이에요 (--settle)");
}

main().catch((e) => { console.error(e); process.exit(1); });
