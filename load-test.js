import http from 'k6/http';
import { check, sleep } from 'k6';

// ================= CONFIG =================
const BASE_URL = 'http://localhost:8080/api/posts';

const USERS = [1,2,3,4,5,6,7,8,9,10];
const BOTS  = [1,2,3,4,5,6,7,8,9,10];

// ================= OPTIONS =================
export const options = {
    thresholds: {
        http_req_failed: ['rate<0.9'],
        http_req_duration: ['p(95)<1500'],
    },
    scenarios: {

        // Horizontal cap (main spam test)
        horizontal_spam: {
            executor: 'constant-arrival-rate',
            rate: 200,
            timeUnit: '1s',
            duration: '10s',
            preAllocatedVUs: 300,
            maxVUs: 500,
            exec: 'botSpamHorizontal',
        },

        // Cooldown test
        cooldown_spam: {
            executor: 'constant-arrival-rate',
            rate: 50,
            timeUnit: '1s',
            duration: '10s',
            preAllocatedVUs: 100,
            exec: 'botSpamCooldown',
        },

        // Comment depth test
        depth_test: {
            executor: 'per-vu-iterations',
            vus: 1,
            iterations: 1,
            exec: 'depthTest',
        },

        // like test
        like_posts: {
            executor: 'constant-vus',
            vus: 10,
            duration: '10s',
            exec: 'likePost',
        }
    }
};

// ================= SETUP =================
export function setup() {

    // 1. USER post (for cooldown test)
    const userPostRes = http.post(BASE_URL, JSON.stringify({
        content: "Cooldown test post",
        authorId: USERS[0],
        authorType: "USER"
    }), { headers: { 'Content-Type': 'application/json' } });

    // 2. BOT post (for horizontal cap test)
    const botPostRes = http.post(BASE_URL, JSON.stringify({
        content: "Horizontal cap test post",
        authorId: BOTS[0],
        authorType: "BOT"
    }), { headers: { 'Content-Type': 'application/json' } });

    if (userPostRes.status !== 201 || botPostRes.status !== 201) {
        throw new Error("Setup failed");
    }

    // 3. Depth test post (USER or BOT both fine)
    const depthPostRes = http.post(BASE_URL, JSON.stringify({
        content: "Depth test post",
        authorId: USERS[0],
        authorType: "USER"
    }), { headers: { 'Content-Type': 'application/json' } });

    if (userPostRes.status !== 201 || botPostRes.status !== 201 || depthPostRes.status !== 201) {
        throw new Error("Setup failed");
    }

    const userPostId = JSON.parse(userPostRes.body).id;
    const botPostId  = JSON.parse(botPostRes.body).id;
    const depthPostId = JSON.parse(depthPostRes.body).id;

    console.log(`✅ Cooldown postId=${userPostId}`);
    console.log(`✅ Horizontal postId=${botPostId}`);
    console.log(`✅ Depth postId=${depthPostId}`);

    return {
        cooldownPostId: userPostId,
        horizontalPostId: botPostId,
        depthPostId: depthPostId
    };
}

// ================= HELPERS =================

function randomBot() {
    return BOTS[Math.floor(Math.random() * BOTS.length)];
}

function logUnexpected(res, label) {
    if (res.status >= 400 && res.status !== 429) {
        console.error(`[${label}] status=${res.status} body=${res.body}`);
    }
}

// ================= TEST CASES =================

// Horizontal Cap Test (NO cooldown)
export function botSpamHorizontal(data) {

    const payload = JSON.stringify({
        authorId: randomBot(),
        authorType: "BOT",
        content: `Horizontal spam ${Math.random()}`,
        parentCommentId: null
    });

    const res = http.post(
        `${BASE_URL}/${data.horizontalPostId}/comments`,
        payload,
        { headers: { 'Content-Type': 'application/json' } }
    );

    check(res, {
        'horizontal: 201 or 429': (r) => r.status === 201 || r.status === 429,
        'horizontal: no 500': (r) => r.status < 500,
    });

    logUnexpected(res, 'HORIZONTAL');
}

// Cooldown Test
export function botSpamCooldown(data) {

    const payload = JSON.stringify({
        authorId: randomBot(),
        authorType: "BOT",
        content: `Cooldown spam ${Math.random()}`,
        parentCommentId: null
    });

    const res = http.post(
        `${BASE_URL}/${data.cooldownPostId}/comments`,
        payload,
        { headers: { 'Content-Type': 'application/json' } }
    );

    check(res, {
        'cooldown: 201 or 429': (r) => r.status === 201 || r.status === 429,
        'cooldown: no 500': (r) => r.status < 500,
    });

    logUnexpected(res, 'COOLDOWN');
}

// Comment depth test
export function depthTest(data) {

    let parentId = null;
    let lastResponse = null;

    for (let i = 0; i < 21; i++) {

        const payload = JSON.stringify({
            authorId: USERS[0],  // human to avoid cooldown issues
            authorType: "USER",
            content: `Depth level ${i}`,
            parentCommentId: parentId
        });

        const res = http.post(
            `${BASE_URL}/${data.depthPostId}/comments`,
            payload,
            { headers: { 'Content-Type': 'application/json' } }
        );

        // First 20 should succeed
        if (i < 21) {
            check(res, {
                [`depth level ${i} created`]: (r) => r.status === 201
            });

            const body = JSON.parse(res.body);
            parentId = body.id;
        }
        // 21st should fail
        else {
            check(res, {
                'depth limit enforced': (r) => r.status >= 400 && r.status < 500
            });
        }

        lastResponse = res;
    }

    console.log("✅ Depth test completed");
}

// Like test
export function likePost(data) {

    const res = http.post(`${BASE_URL}/${data.cooldownPostId}/like`);

    check(res, {
        'like success': (r) => r.status === 200,
    });

    sleep(0.2);
}